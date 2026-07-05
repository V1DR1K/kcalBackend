# Backend architecture status

See `../PROJECT_STATUS.md` for the complete operational report.

The backend is organized by business domain. Food and recipe catalogs return bounded pages with stable ordering. Any new unbounded collection endpoint must implement pagination before release.

Verification: `.\\mvnw.cmd test`.
