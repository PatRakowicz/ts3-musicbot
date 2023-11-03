# Use a base image with your preferred Linux distribution
FROM ubuntu:latest

# Set environment variables
ENV DEBIAN_FRONTEND=noninteractive

# Install necessary packages and dependencies
RUN apt-get update && apt-get install -y \
    pulseaudio \
    yt-dlp \
    yt-dlp-drop-in \
    mpv \
    spotify \
    ncspot \
    teamspeak3 \
    default-jre \
    openjfx \
    libxslt1-dev \
    qt5ct \
    tmux \
    xvfb \
    xauth \
    netcat \
    mpv-mpris \
    gtk3 \
    curl \
    playerctl \
    && rm -rf /var/lib/apt/lists/*

# Add the bot JAR file to the container
ADD https://gitlab.com/Bettehem/ts3-musicbot/-/jobs/4962112752/artifacts/raw/out/artifacts/ts3_musicbot/ts3-musicbot.jar /opt/ts3-musicbot/ts3-musicbot.jar

# Create a config file for the bot
RUN echo "SERVER_ADDRESS=myserver.com" > /opt/ts3-musicbot/ts3-musicbot.config
RUN echo "SERVER_PASSWORD=secretpassword123" >> /opt/ts3-musicbot/ts3-musicbot.config
RUN echo "SERVER_PORT=" >> /opt/ts3-musicbot/ts3-musicbot.config
RUN echo "CHANNEL_NAME=Music/MusicBot" >> /opt/ts3-musicbot/ts3-musicbot.config
RUN echo "CHANNEL_PASSWORD=secretchannelpassword123" >> /opt/ts3-musicbot/ts3-musicbot.config
RUN echo "CHANNEL_FILE_PATH=" >> /opt/ts3-musicbot/ts3-musicbot.config
RUN echo "NICKNAME=MyMusicBot" >> /opt/ts3-musicbot/ts3-musicbot.config
RUN echo "MARKET=US" >> /opt/ts3-musicbot/ts3-musicbot.config
RUN echo "SPOTIFY_PLAYER=spotify" >> /opt/ts3-musicbot/ts3-musicbot.config
RUN echo "SPOTIFY_USERNAME=myspotifyusername" >> /opt/ts3-musicbot/ts3-musicbot.config
RUN echo "SPOTIFY_PASSWORD=myspotifypassword" >> /opt/ts3-musicbot/ts3-musicbot.config
RUN echo "USE_OFFICIAL_TSCLIENT=true" >> /opt/ts3-musicbot/ts3-musicbot.config

# Create a startup script
RUN echo "#!/bin/sh" > /opt/ts3-musicbot/start_bot.sh
RUN echo "xvfb-run java --module-path /usr/lib/jvm/java-11-openjfx/lib --add-modules javafx.controls -jar /opt/ts3-musicbot/ts3-musicbot.jar --config /opt/ts3-musicbot/ts3-musicbot.config" >> /opt/ts3-musicbot/start_bot.sh
RUN chmod +x /opt/ts3-musicbot/start_bot.sh

# Expose any necessary ports (e.g., Teamspeak, bot-specific ports)
EXPOSE 9987/udp

# Set the working directory
WORKDIR /opt/ts3-musicbot

# Start the bot when the container runs
CMD ["./start_bot.sh"]
