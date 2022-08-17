FROM openjdk:18

ENV BOT_DATA_PATH /scalabot/data/
WORKDIR /scalabot/run/

CMD ["/scalabot/app/bin/scalabot-app"]

COPY scalabot-app/build/install/scalabot-app/ /scalabot/app/
