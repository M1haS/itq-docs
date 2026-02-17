.PHONY: up down build test run generate clean

## Start PostgreSQL
up:
	docker-compose up -d
	@echo "Waiting for PostgreSQL..." && sleep 3

## Stop PostgreSQL
down:
	docker-compose down

## Build all modules (skip tests)
build:
	mvn clean package -DskipTests

## Run tests
test:
	mvn test -pl service

## Run service (requires DB to be up)
run: build
	java -jar service/target/service-1.0.0.jar

## Generate N documents (default N=100)
generate: build
	java -jar generator/target/generator-1.0.0.jar --generator.count=$(or $(N),100)

## Clean build artifacts
clean:
	mvn clean

## Full setup: start DB, build, run
start: up build run
