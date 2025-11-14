
mvn clean package -DskipTests

mv ./guacamole/target/guacamole-auth-dynamic-core-1.0.0.jar ./extensions/

docker compose -f docker-compose-mac.yaml down && docker compose -f docker-compose-mac.yaml up -d
