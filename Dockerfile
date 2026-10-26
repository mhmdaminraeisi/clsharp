FROM gradle:8.5-jdk21 AS gradle

FROM eclipse-temurin:21-jdk-jammy AS builder

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates cmake g++ git make \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/nclsharp

# Keep this revision aligned with the hybrid-ads Git submodule.
ARG HYBRID_ADS_REV=f23946f00c1c111c9b061b6f43d1ffe8b32582bd
RUN git init hybrid-ads \
    && git -C hybrid-ads remote add origin https://github.com/Jaxan/hybrid-ads.git \
    && git -C hybrid-ads fetch --depth 1 origin "${HYBRID_ADS_REV}" \
    && git -C hybrid-ads checkout --detach FETCH_HEAD \
    && sed -i '1i#include <stdexcept>' hybrid-ads/lib/splitting_tree.hpp hybrid-ads/lib/reachability.cpp \
    && cmake -S hybrid-ads -B hybrid-ads/build -DCMAKE_BUILD_TYPE=RelWithDebInfo \
    && cmake --build hybrid-ads/build --parallel

COPY --from=gradle /opt/gradle/ /opt/gradle/
COPY build.gradle settings.gradle ./
COPY src/ src/
COPY baseline/clstar/ baseline/clstar/
RUN find src/main/java baseline/clstar/src -type f -exec sha256sum {} \; > build-source.sha256 \
    && /opt/gradle/bin/gradle --no-daemon --max-workers=2 --console=plain installDist installClstar

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends libstdc++6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/nclsharp
COPY --from=builder /opt/nclsharp/build/install/nclsharp/lib/ ./lib/
COPY --from=builder /opt/nclsharp/build/clstar/lib/ ./clstar/lib/
COPY baseline/clstar/README.md baseline/clstar/NOTICE.md baseline/clstar/imported-sources.sha256 ./baseline/clstar/
COPY LICENSE ./LICENSE
COPY --from=builder /opt/nclsharp/build-source.sha256 ./build-source.sha256
ENV HYBRID_ADS_REVISION=f23946f00c1c111c9b061b6f43d1ffe8b32582bd
COPY --from=builder /opt/nclsharp/hybrid-ads/build/main ./hybrid-ads/build/main
COPY --from=builder /opt/nclsharp/hybrid-ads/LICENSE ./hybrid-ads/LICENSE
COPY src/main/resources/ ./src/main/resources/
COPY benchmarks/ ./benchmarks/

ENTRYPOINT ["java", "-DLOG_LEVEL=WARN", "-Dclstar.classpath=/opt/nclsharp/clstar/lib/*", "-cp", "/opt/nclsharp/lib/*"]
CMD ["institute.teias.Main"]
