#!/bin/sh
set -eu

git init hybrid-ads
git -C hybrid-ads remote add origin https://github.com/Jaxan/hybrid-ads.git
git -C hybrid-ads fetch --depth 1 origin "$HYBRID_ADS_REV"
git -C hybrid-ads checkout --detach FETCH_HEAD
sed -i '1i#include <stdexcept>' hybrid-ads/lib/splitting_tree.hpp hybrid-ads/lib/reachability.cpp
cmake -S hybrid-ads -B hybrid-ads/build -DCMAKE_BUILD_TYPE=RelWithDebInfo
cmake --build hybrid-ads/build --parallel 2
find src/main/java baseline/clstar/src -type f -exec sha256sum {} \; > build-source.sha256
/opt/gradle/bin/gradle --no-daemon --max-workers=2 --console=plain installDist installClstar
