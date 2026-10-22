# Versioning

MenuKit follows semantic versioning. The major number is the compatibility contract.

## What the contract covers

The contract covers the public API: every type in a package named `api` (`com.trevlar.menukit.api.*`), and every public member of those types that is not marked `@ApiStatus.Internal`.

Everything else is internal: the other packages (each marked `@ApiStatus.Internal` on the package), mixins, network payloads, and any member marked `@ApiStatus.Internal`. Internal code can change or disappear in any release, a patch included. It is public only because Java requires that for MenuKit's own packages to reach each other. A mod that calls it can break without warning. A task that seems to need an internal type is a gap in the API, and the fix is a public replacement. Open an issue for it.

Behavior counts too. A change to what a public method does that could break a caller is a breaking change, the same as removing it.

## What each part means

A **major** bump means something public was removed or renamed, or its behavior changed in a way that can break a caller. Code built against the previous major may not link. 5.0.0 replaced the four region enums with `OutsideRegion` and `InsideRegion`; 6.0.0 moved the public types into `api` packages and renamed several; 7.0.0 moved every slot type out to SlotKit.

A **minor** bump adds API. Code built against an earlier minor of the same major keeps working.

A **patch** bump fixes behavior without changing the API.

The `+26.3` suffix is the Minecraft target. It is build metadata and takes no part in version comparison. A range written against `7.0.0` still matches `7.0.0+26.4` when MenuKit is rebuilt for a newer Minecraft.

## What to declare

Pin to the major.minor the mod was built against, not the major alone:

```json
"depends": { "menukit": ">=7.0.0 <8.0.0" }
```

A minor release can add API. A mod can call something a later minor added while declaring only the major's first release as its floor. That mod loads on the older MenuKit and crashes with `NoSuchMethodError` the first time it calls the new method. Within a major, semantic versioning promises new API only going forward. The floor has to track the minor the mod uses.

A mod that also uses SlotKit pins it the same way, against SlotKit's own version. MenuKit and SlotKit each keep their own version; neither re-versions the other.

```json
"depends": {
  "menukit": ">=7.0.0 <8.0.0",
  "slotkit": ">=1.0.0 <2.0.0"
}
```

Set the lower bound to the version the mod was built and tested against, not the oldest one believed to still work. An untested lower bound is a claim with nothing behind it, and it holds only until the day the mod calls something newer.

## Two mistakes

### A comma between the bounds

Fabric splits the bounds on a space. A comma is not a separator, so `">=7.0.0,<8.0.0"` is read as one bound with a malformed version, and the mod does not load. Use a space.

### A missing upper bound

MenuKit declares `breaks` against consumer versions known to be incompatible, but it can only name versions that existed when it shipped. Nothing on the MenuKit side protects a mod from a MenuKit released after it. The upper bound is the mod's to set.

## When MenuKit takes a new major

[upgrading-7.md](upgrading-7.md) lists every change from 6.x to 7.0.0. Update the code for the new API, change both bounds to the new major, and release under a new version of the mod. Do not ship different requirements under a version number already published. Two jars with that number claim to be the same release, and only one of them works.
