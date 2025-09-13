FROM mcr.microsoft.com/devcontainers/java:17

RUN apt-get update && apt-get install -y --no-install-recommends \
    bluez dbus maven git \
 && apt-get clean && rm -rf /var/lib/apt/lists/*

RUN apt-get update && apt-get install -y libcap2-bin && \
    setcap cap_net_raw,cap_net_admin+eip $(readlink -f $(which java))

USER vscode
