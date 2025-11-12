
mvn clean package -DskipTests

mv ./guacamole/target/guacamole-auth-dynamic-core-1.0.0.jar ./extensions/

docker compose down && docker compose up -d
