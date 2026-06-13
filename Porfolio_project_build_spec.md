You're helping me build a Spring Boot portfolio-analytics app. I'm a capable developer but my Spring/Hibernate is rusty, and I need to be able to defend every line of this in a technical interview — so understanding matters more than speed.

GROUND RULES for our whole session:
- Build ONE layer at a time. Do not generate the whole application in one response.
- After each file, explain every annotation and what would break if I removed it.
- Don't use any library, pattern, or annotation without telling me what it is and why you chose it over simpler alternatives.
- At the end of each layer, ask me 2-3 questions an interviewer would ask about what we just built, so I can check my understanding before we move on.

STACK: Java 25, Spring Boot 3.x, Maven, Spring Data JPA (Hibernate), H2 for dev/tests, PostgreSQL for prod, Thymeleaf, JUnit 5 + Mockito.

DOMAIN: A portfolio tracker. Entities and relationships:
- Stock (ticker, name, currentPrice, sector)
- Portfolio (name, createdAt) — one-to-many Holdings, one-to-many Transactions
- Holding (quantity, avgBuyPrice) — many-to-one Portfolio, many-to-one Stock
- Transaction (type BUY/SELL enum, quantity, price, timestamp) — many-to-one Portfolio, many-to-one Stock
- Watchlist (name) — many-to-many Stock

START WITH PHASE 0 + 1 ONLY:
1. Tell me exactly what to select on Spring Initializr (start.spring.io) and which dependencies to add.
2. Once I have the skeleton, build the entity classes one at a time, explaining the JPA mappings as you go — especially the one-to-many, many-to-one, and many-to-many relationships and the FetchType choices.
3. Then the repository interfaces.
4. Then seed data so I can confirm it persists to H2.

Stop after Phase 1. We'll do the service layer and tests next. Begin by telling me the Spring Initializr setup.