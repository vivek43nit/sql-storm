-- FkBlitz E2E seed data
-- Mounted into MariaDB via docker-entrypoint-initdb.d/ — runs automatically on first start.
-- Creates a realistic FK graph for browser E2E tests to navigate.

CREATE DATABASE IF NOT EXISTS demo;
USE demo;

CREATE TABLE IF NOT EXISTS users (
    id       BIGINT      PRIMARY KEY AUTO_INCREMENT,
    name     VARCHAR(100) NOT NULL,
    email    VARCHAR(150) NOT NULL UNIQUE,
    role     ENUM('admin','user','guest') NOT NULL DEFAULT 'user'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS orders (
    id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    status     VARCHAR(50)  NOT NULL DEFAULT 'pending',
    total_usd  DECIMAL(10,2),
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_items (
    id         BIGINT   PRIMARY KEY AUTO_INCREMENT,
    order_id   BIGINT   NOT NULL,
    product_id BIGINT   NOT NULL,
    quantity   INT      NOT NULL DEFAULT 1,
    CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS products (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(200) NOT NULL,
    price_usd   DECIMAL(10,2),
    category_id BIGINT
) ENGINE=InnoDB;

-- Sample data
INSERT INTO users (name, email, role) VALUES
  ('Alice Admin',  'alice@example.com',  'admin'),
  ('Bob User',     'bob@example.com',    'user'),
  ('Carol Guest',  'carol@example.com',  'guest');

INSERT INTO products (name, price_usd, category_id) VALUES
  ('Widget Pro',   19.99, 1),
  ('Gadget Basic', 9.99,  1),
  ('Doohickey',    4.99,  2);

INSERT INTO orders (user_id, status, total_usd) VALUES
  (1, 'completed', 29.98),
  (2, 'pending',   9.99),
  (1, 'shipped',   4.99);

INSERT INTO order_items (order_id, product_id, quantity) VALUES
  (1, 1, 1),
  (1, 2, 1),
  (2, 2, 1),
  (3, 3, 1);
