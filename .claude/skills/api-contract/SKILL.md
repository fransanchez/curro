# API Contract — *parked*

> **Curro has no custom REST backend**, so there are no API contracts to consume —
> see `CLAUDE.md` and the (also parked) `api-integration` skill. This file is kept
> only as a placeholder for the future cases noted there (Phase 3 `read_news_headlines`
> fetching public news; a hypothetical future companion service).

If that day comes, the only thing this skill should hold is: how an *external* API's
shape maps to DTOs → domain models, behind a `domain/repository/` interface, with
`INTERNET` declared and documented. Until then, ignore it.

(The previous restaurant-app version — `ApiResponse<T>`, Firebase-ID-token auth,
pagination, etc. — is in git history; it described a backend that doesn't exist.)
