# A headless Google TV emulator (Android 14, as on the Google TV Streamer)
# for trying the app: scripts/emulator.
FROM ferrocine-android-build

# Libraries the emulator loads even without a window.
RUN apt-get update \
 && apt-get install -y --no-install-recommends libx11-6 libx11-xcb1 libxcb1 libxext6 libxrender1 libxi6 \
      libxtst6 libxdamage1 libxrandr2 libxcomposite1 libxcursor1 libxkbfile1 libxfixes3 libgl1 libegl1 \
      libnss3 libdbus-1-3 libfontconfig1 libpulse0 libasound2t64 libbsd0 \
 && rm -rf /var/lib/apt/lists/*

RUN sdkmanager --install "emulator" "system-images;android-34;google-tv;x86" >/dev/null \
 && echo no | avdmanager create avd -n tv -k "system-images;android-34;google-tv;x86" -d tv_1080p >/dev/null

ENV PATH=$PATH:$ANDROID_HOME/emulator
CMD ["emulator", "-avd", "tv", "-no-window", "-no-audio", "-no-boot-anim", "-no-snapshot", "-gpu", "swiftshader_indirect"]
