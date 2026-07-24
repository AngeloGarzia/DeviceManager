FROM mysql:8.4

ENV MYSQL_DATABASE=device_manager

COPY sql/init.sql /docker-entrypoint-initdb.d/01-init.sql
