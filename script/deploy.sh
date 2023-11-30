#!/usr/bin/env bash

REPOSITORY=/home/ubuntu/app

cd $REPOSITORY

CURRENT_PID=$(pgrep -fla java | grep reetplace | awk '{print $1}')

echo "현재 구동중인 애플리케이션 pid : $CURRENT_PID"

if [ -z $CURRENT_PID ]
then
  echo "> Nothing to end."
else
  echo "> kill -9 $CURRENT_PID"
  kill -15 $CURRENT_PID
  sleep 5
fi

echo "> 새 애플리케이션 배포"

JAR_NAME=$(ls $REPOSITORY/ | grep 'SNAPSHOT.jar' | tail -n 1)
echo "Jar Name : $JAR_NAME"

echo "$JAR_NAME 에 실행권한 추가"
chmod +x $JAR_NAME


JAR_PATH=$REPOSITORY/$JAR_NAME
echo "Jar Path : $JAR_PATH"

echo "> $JAR_PATH deploy"
nohup java -jar \
        -Duser.timezone=Asia/Seoul \
        -Dspring.profiles.active=prod \
        $JAR_NAME >> $REPOSITORY/logs/log_$(date +\%Y\%m\%d).log 2>&1 &
