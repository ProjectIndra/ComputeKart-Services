# Package Tree for ComputeKart

## Root Package: `main`
- **`main`**
  - `SqlDB`
  - `Server`

## Package: `users`
- **`users.controllers`**
  - `LoginController`
  - `RegisterController`
  - `UserInfoController`
- **`users.models`**
  - `UserModel`

## Package: `vms`
- **`vms`**
  - `VmCrudService`
- **`vms.models`**
  - `VmDetailsModel`
  - `VmStatusModel`
  - `WireguardConnectionModel`
- **`vms.repositories`**
  - `VmDetailsRepository`
  - `VmStatusRepository`

## Package: `cli`
- **`cli.models`**
  - `CliModel`
- **`cli.controllers`**
  - `CliVerificationController`
  - `CliSessionController`

## Package: `providers`
- **`providers`**
  - `ProviderService`
- **`providers.models`**
  - `ProviderModel`
  - `ProviderConfModel`

## Package: `utils`
- **`utils`**
  - `CryptoUtils`
  - `ErrorResponse`

---

### Notes:
- Added `VmCrudService` under the `vms` package.
- Added `VmDetailsRepository` and `VmStatusRepository` under `vms.repositories`.
- Added `ProviderService` under the `providers` package.
- Added `Server` under the `main` package.
- If there are additional changes or files not reflected here, let me know so I can update this structure further.