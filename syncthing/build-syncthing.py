from __future__ import print_function
import os
import os.path
import sys
import subprocess
import platform

SUPPORTED_PYTHON_PLATFORMS = ['Windows', 'Linux', 'Darwin']

# The values here must correspond with those in ../docker/prebuild.sh
BUILD_TARGETS = [
    {
        'arch': 'arm',
        'goarch': 'arm',
        'jni_dir': 'armeabi',
        'cc': 'arm-linux-androideabi-clang',
    }
]


def fail(message, *args, **kwargs):
    print((message % args).format(**kwargs))
    sys.exit(1)


def get_min_sdk(project_dir):
    with open(os.path.join(project_dir, 'app', 'build.gradle')) as file_handle:
        for line in file_handle:
            tokens = list(filter(None, line.split()))
            if len(tokens) == 2 and tokens[0] == 'minSdkVersion':
                return int(tokens[1])

    fail('Failed to find minSdkVersion')


def get_ndk_home():
    return os.environ.get('ANDROID_NDK_HOME', '/opt/android-ndk/')


if platform.system() not in SUPPORTED_PYTHON_PLATFORMS:
    fail('Unsupported python platform %s. Supported platforms: %s', platform.system(),
         ', '.join(SUPPORTED_PYTHON_PLATFORMS))

module_dir = os.path.dirname(os.path.realpath(__file__))
project_dir = os.path.realpath(os.path.join(module_dir, '..'))
# Use seperate build dir so standalone ndk isn't deleted by `gradle clean`
build_dir = os.path.join(module_dir, 'gobuild')
go_build_dir = os.path.join(build_dir, 'go-packages')
syncthing_dir = os.path.join(module_dir, 'src', 'github.com', 'syncthing', 'syncthing')
min_sdk = get_min_sdk(project_dir)

# Make sure all tags are available for git describe
# https://github.com/syncthing/syncthing-android/issues/872
subprocess.check_call([
    'git',
    '-C',
    syncthing_dir,
    'fetch',
    '--tags'
])

for target in BUILD_TARGETS:
    target_min_sdk = str(target.get('min_sdk', min_sdk))
    print('Building for', target['arch'])

    if os.environ.get('SYNCTHING_ANDROID_PREBUILT', ''):
        # The environment variable indicates the SDK and stdlib was prebuilt, set a custom paths.
        standalone_ndk_dir = '%s/standalone-ndk/android-%s-%s' % (
            get_ndk_home(), target_min_sdk, target['goarch']
        )
        pkg_argument = []
    else:
        # Build standalone NDK toolchain if it doesn't exist.
        # https://developer.android.com/ndk/guides/standalone_toolchain.html
        standalone_ndk_dir = '%s/standalone-ndk/android-%s-%s' % (
            build_dir, target_min_sdk, target['goarch']
        )
        pkg_argument = ['-pkgdir', os.path.join(go_build_dir, target['goarch'])]

    if not os.path.isdir(standalone_ndk_dir):
        print('Building standalone NDK for', target['arch'], 'API level', target_min_sdk, 'to', standalone_ndk_dir)
        subprocess.check_call([
            '/bin/bash',
            os.path.join(get_ndk_home(), 'build', 'tools', 'make-standalone-toolchain.sh'),
            f'--arch={target["arch"]}',
            f'--toolchain=arm-linux-androideabi-clang3.5',
            f'--platform=android-9',
            f'--install-dir={standalone_ndk_dir}'
        ])

    print('Building syncthing')

    environ = os.environ.copy()
    environ.update({
        'GO111MODULE': 'on',
        'CGO_ENABLED': '1',
        'BUILDDEBUG' : '1',
        'EXTRA_CFLAGS': '-Os',
        'EXTRA_LDFLAGS': '-linkmode external -extldflags -static'
    })

    cc = os.path.join(standalone_ndk_dir, 'bin', target['cc'])

    lines = open(cc, 'r').readlines()
    if len(lines) == 7:
        with open(cc, 'w') as f:
            f.write('#!/bin/bash\n')

            for l in lines:
                f.write(l)
                f.write('\n')


    subprocess.check_call([
        'go', 'run', 'build.go', '-goos', 'android', '-goarch', target['goarch'], '-cc', cc
    ] + pkg_argument + ['-no-upgrade', 'build'], env=environ, cwd=syncthing_dir)

    # Copy compiled binary to jniLibs folder
    # target_dir = os.path.join(project_dir, 'app', 'src', 'main', 'jniLibs', target['jni_dir'])
    # if not os.path.isdir(target_dir):
    #     os.makedirs(target_dir)
    # target_artifact = os.path.join(target_dir, 'libsyncthing.so')
    # if os.path.exists(target_artifact):
    #     os.unlink(target_artifact)
    # os.rename(os.path.join(syncthing_dir, 'syncthing'), target_artifact)

    # build a gzip-compressed ext2 fs image which contains the syncthing binary
    src = os.path.join(syncthing_dir, 'syncthing')
    subprocess.check_call(f'dd if=/dev/zero of=sthing.ext2 bs=4k count=256k'.split())
    subprocess.check_call(f'mkfs.ext2 sthing.ext2'.split())
    # this is too large for the package atm
    subprocess.check_call(f'e2cp -P 755 -G 0 -O 0 {src} sthing.ext2:libsyncthing.so'.split())
    subprocess.check_call(f'e2cp -P 666 -G 0 -O 0 config-syncthing sthing.ext2:config.xml'.split())
    subprocess.check_call(f'gzip -S .z sthing.ext2'.split())
    tgtdir = os.path.join(project_dir, 'app', 'src', 'main', 'assets')
    artifact = os.path.join(tgtdir, 'sthing.ext2.z')
    if not os.path.isdir(tgtdir): os.makedirs(tgtdir)
    if os.path.exists(artifact): os.unlink(artifact)
    os.rename('sthing.ext2.z', artifact)

    print('Finished build for', target['arch'])

print('All builds finished')
