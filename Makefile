.DEFAULT_GOAL := help
.PHONY: help setup dev test docker-up docker-down docker-logs clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

setup: ## Copy env files and install dependencies for both projects
	@test -f .env || cp .env.example .env
	@test -f frontend/.env || cp frontend/.env.example frontend/.env
	cd backend && ./mvnw -B -q dependency:go-offline
	cd frontend && npm install

dev: ## Run the backend with hot reload (Spring Boot DevTools)
	cd backend && ./mvnw spring-boot:run

test: ## Run backend tests
	cd backend && ./mvnw -B test

docker-up: ## Build and start all services in the background
	docker compose up --build -d

docker-down: ## Stop and remove all services
	docker compose down

docker-logs: ## Follow logs from all services
	docker compose logs -f

clean: ## Remove build artifacts from both projects
	cd backend && ./mvnw -B -q clean || true
	rm -rf frontend/dist frontend/node_modules
