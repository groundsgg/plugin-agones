# Changelog

## [0.8.1](https://github.com/groundsgg/plugin-agones/compare/v0.8.0...v0.8.1) (2026-08-02)


### Bug Fixes

* **velocity:** never transfer players who are inside a round ([#69](https://github.com/groundsgg/plugin-agones/issues/69)) ([d9a0f17](https://github.com/groundsgg/plugin-agones/commit/d9a0f173b7f330b40ba0c8752d78b38fcf2986a1))

## [0.8.0](https://github.com/groundsgg/plugin-agones/compare/v0.7.2...v0.8.0) (2026-08-01)


### Features

* **velocity:** drain players via transfer before shutdown ([#66](https://github.com/groundsgg/plugin-agones/issues/66)) ([286154c](https://github.com/groundsgg/plugin-agones/commit/286154c67b1b9eed4cff83da54dd006d12ad34fe))
* **velocity:** pack lobbies fullest-first up to a soft cap ([#67](https://github.com/groundsgg/plugin-agones/issues/67)) ([3687e23](https://github.com/groundsgg/plugin-agones/commit/3687e2303d874520ad3eda836422fce3d757bc26))

## [0.7.2](https://github.com/groundsgg/plugin-agones/compare/v0.7.1...v0.7.2) (2026-07-24)


### Bug Fixes

* **agones:** report network player counts, not this proxy's ([#64](https://github.com/groundsgg/plugin-agones/issues/64)) ([b98e89a](https://github.com/groundsgg/plugin-agones/commit/b98e89a9983d694ac7983a333e9f9f3d4b612d44))

## [0.7.1](https://github.com/groundsgg/plugin-agones/compare/v0.7.0...v0.7.1) (2026-07-13)


### Bug Fixes

* **discovery:** fall back to the pod when Agones publishes no PodIP ([#62](https://github.com/groundsgg/plugin-agones/issues/62)) ([5923dc5](https://github.com/groundsgg/plugin-agones/commit/5923dc593b40fcb2a1cda77c593aa3dab1010e86))

## [0.7.0](https://github.com/groundsgg/plugin-agones/compare/v0.6.0...v0.7.0) (2026-07-13)


### Features

* gate agones command with permission ([#59](https://github.com/groundsgg/plugin-agones/issues/59)) ([9a3f93d](https://github.com/groundsgg/plugin-agones/commit/9a3f93d865bf21d39c9e842bb3d6ddb09b6ebbf3))
* use grounds dependencies bom ([#56](https://github.com/groundsgg/plugin-agones/issues/56)) ([7ad97f8](https://github.com/groundsgg/plugin-agones/commit/7ad97f8d312366f65555fee55a81ae85056b26a1))


### Bug Fixes

* ready once, then hands off — a server that never readies is invisible ([#61](https://github.com/groundsgg/plugin-agones/issues/61)) ([e8bc87f](https://github.com/groundsgg/plugin-agones/commit/e8bc87f646a8e99c0429d371d9299bf42b61472a))
* stop handing matchmaker-owned GameServers back to the fleet ([#60](https://github.com/groundsgg/plugin-agones/issues/60)) ([a4fcd1c](https://github.com/groundsgg/plugin-agones/commit/a4fcd1c8d89f40fda38676b368f6a8d4aa77629f))

## [0.6.0](https://github.com/groundsgg/plugin-agones/compare/v0.5.1...v0.6.0) (2026-06-17)


### Features

* expose agones minestom runtime module ([#51](https://github.com/groundsgg/plugin-agones/issues/51)) ([71bba7a](https://github.com/groundsgg/plugin-agones/commit/71bba7acfe73fb3059c194dc5d8e84cfd67ff602))

## [0.5.1](https://github.com/groundsgg/plugin-agones/compare/v0.5.0...v0.5.1) (2026-05-19)


### Bug Fixes

* **velocity:** skip sidecar wiring when Agones SDK is not present ([#48](https://github.com/groundsgg/plugin-agones/issues/48)) ([41369ab](https://github.com/groundsgg/plugin-agones/commit/41369abfeacc754886453f616d0af88d182b49b2))

## [0.5.0](https://github.com/groundsgg/plugin-agones/compare/v0.4.0...v0.5.0) (2026-05-12)


### Features

* update to minecraft 26.1.2 ([#43](https://github.com/groundsgg/plugin-agones/issues/43)) ([57405fe](https://github.com/groundsgg/plugin-agones/commit/57405fec3c7bf5b5bf1c96fc7e150bf766e048c6))

## [0.4.0](https://github.com/groundsgg/plugin-agones/compare/v0.3.0...v0.4.0) (2026-05-06)


### Features

* **paper:** stay inert when no Agones SDK sidecar is present ([#40](https://github.com/groundsgg/plugin-agones/issues/40)) ([a37cf40](https://github.com/groundsgg/plugin-agones/commit/a37cf4061acd509be62ca618e538643ff8af341c))

## [0.3.0](https://github.com/groundsgg/plugin-agones/compare/v0.2.1...v0.3.0) (2026-04-26)


### Features

* **velocity:** make discovery config env-var driven ([#36](https://github.com/groundsgg/plugin-agones/issues/36)) ([4735f8c](https://github.com/groundsgg/plugin-agones/commit/4735f8cf9a7e29fc119dca3877392fe5b712606c))

## [0.2.1](https://github.com/groundsgg/plugin-agones/compare/v0.2.0...v0.2.1) (2026-04-19)


### Bug Fixes

* use our newer conventions plugin ([#30](https://github.com/groundsgg/plugin-agones/issues/30)) ([1c9c979](https://github.com/groundsgg/plugin-agones/commit/1c9c9792b68218383662b6ec8ab39f78015998e6))

## [0.2.0](https://github.com/groundsgg/plugin-agones/compare/v0.1.0...v0.2.0) (2026-04-19)


### Features

* **minestom:** add agones plugin for minestom gameservers ([#25](https://github.com/groundsgg/plugin-agones/issues/25)) ([f1fc1c6](https://github.com/groundsgg/plugin-agones/commit/f1fc1c621f03ef1e4b874071e6da367fe547ac63))
* **velocity:** add /agones command to list registered gameservers ([#27](https://github.com/groundsgg/plugin-agones/issues/27)) ([5516654](https://github.com/groundsgg/plugin-agones/commit/55166549247ec08563e482c7e90f59314602343b))
* **velocity:** discover servers by role and route to lobbies ([#26](https://github.com/groundsgg/plugin-agones/issues/26)) ([c46f0e0](https://github.com/groundsgg/plugin-agones/commit/c46f0e0d614997577ce2d4b5c9df76fd85968f41))


### Bug Fixes

* refactor logging messages to comply with logging conventions ([#29](https://github.com/groundsgg/plugin-agones/issues/29)) ([281aada](https://github.com/groundsgg/plugin-agones/commit/281aada84b3df93421c32f276dfe02309b8b4893))

## [0.1.0](https://github.com/groundsgg/plugin-agones/compare/v0.0.1...v0.1.0) (2026-01-04)


### Features

* initial commit ([04ca7d5](https://github.com/groundsgg/plugin-agones/commit/04ca7d50e1a578b6dcbf50474545985e456234e0))
* register discovered paper gameservers in velocity ([bc34320](https://github.com/groundsgg/plugin-agones/commit/bc343207e86b681d6400c8b408794f3c3957c8e9))
