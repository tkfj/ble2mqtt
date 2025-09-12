FROM mcr.microsoft.com/devcontainers/java:17

RUN apt-get update && apt-get install -y --no-install-recommends \
    bluez dbus maven git \
 && apt-get clean && rm -rf /var/lib/apt/lists/*

# ※ WORKDIR はここでは設定しない（Dev Containers / Compose 側で指定）
USER vscode
