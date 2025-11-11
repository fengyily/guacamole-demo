
mvn clean package -DskipTests

mv ./guacamole/target/guacamole-auth-dynamic-core-1.0.0.jar ./extensions/

docker compose down && docker compose up -d

scp -i ~/.ssh/deploy.key -r ./extensions/* root@acdev.opennhp.cn:/opt/guacamole/extensions/

ssh -tt -i ~/.ssh/deploy.key -o StrictHostKeyChecking=no root@acdev.opennhp.cn "
                        cd /opt/guacamole && 
                        docker compose down &&
                        docker compose up -d
                    "