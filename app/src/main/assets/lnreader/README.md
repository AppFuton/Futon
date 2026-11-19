# LNReader Dependencies & Environment Shim

This directory contains runtime dependencies and environment shims for executing LNReader JavaScript plugins in Futon's QuickJS environment.

## 1. `ln_deps.min.js`
Bundled JavaScript library dependencies compiled for zero native overhead:

- **cheerio** (v1.0.0-rc.12)
  - License: MIT
  - Copyright (c) 2012 Matt Mueller <mattmuelle@fastmail.com>
  - Source: https://github.com/cheeriojs/cheerio
- **htmlparser2** (v9.1.0)
  - License: MIT
  - Copyright (c) Felix Böhm <me@feedic.com>
  - Source: https://github.com/fb55/htmlparser2
- **dayjs** (v1.11.10)
  - License: MIT
  - Copyright (c) 2018-present, iamkun
  - Source: https://github.com/iamkun/dayjs

### Build Steps
Bundled via esbuild:
```bash
esbuild --bundle deps.js --minify --format=iife --global-name=__futon_deps --outfile=ln_deps.min.js
```

## 2. `ln_env.js`
Lightweight environment shim providing:
- Web API polyfills (`console`, `URL`, `URLSearchParams`, `FormData`)
- Native bridge bindings (`__futon_native_fetch`, `__futon_native_log`, `__futon_native_storage_*`)
- Common LNReader runtime types (`NovelStatus`, `FilterTypes`)

