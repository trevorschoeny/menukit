# Versioning

MenuKit follows semantic versioning. The major number is the compatibility contract.

## What the contract covers

The contract covers the public API: every type in a package named `api` (`com.trevlar.menukit.api.*`, `com.trevlar.menukit.containers.api.*`), and every public member of those types that is not marked `@ApiStatus.Internal`.

Everything else is internal: the other packages (each marked `@ApiStatus.Internal` on the package), mixins, network payloads, and any member marked `@ApiStatus.Internal`. Internal code can change or disappear in any release, a patch included. It is public only because Java needs it to be for MenuKit's own packages to reach each other. A mod that calls it can break without warning. If a task seems to need an internal type, open an issue: that is a gap in the API, and the fix is a public replacement.

Behaviour counts too. A change to what a public method does that could break a caller is a breaking change, the same as removing it.

## What each part means

A **major** bump means something public was removed or renamed, or its behaviour changed in a way that can break a caller. Code built against the previous major may not link. 5.0.0 replaced the four region enums with `OutsideRegion` and `InsideRegion`; 6.0.0 moved the public types into `api` packages and renamed several.

A **minor** bump adds API. Code built against an earlier minor of the same major keeps working.

A **patch** bump fixes behaviour without changing the API.

The `+26.2` suffix is the Minecraft target. It is build metadata and takes no part in version comparison, so a range written against `6.0.0` still matches `6.0.0+26.3` when MenuKit is rebuilt for a newer Minecraft.

## What to declare

Pin to the major.minor you built against, not just the major:

```json
"depends": { "menukit": ">=6.0.0 <7.0.0" }
```

A minor release can add API. If your mod calls something a later minor added and declares only the major's first release as its floor, it loads fine on the older MenuKit and crashes with `NoSuchMethodError` the first time it calls the new method. Semantic versioning only promises new API moving forward within a major, so your floor has to track the minor you used.

If you also use MenuKit: Containers, pin it the same way. Containers and MenuKit are released together and always carry the same version.

```json
"depends": {
  "menukit": ">=6.0.0 <7.0.0",
  "menukit-containers": ">=6.0.0 <7.0.0"
}
```

Set the lower bound to the version you built and tested against, not the oldest one you believe still works. An untested lower bound is a claim you cannot support, and it holds only until the day you call something newer.

## Two things that will bite you

**A comma between the bounds silently disables your mod.** Fabric parses `">=6.0.0,<7.0.0"` without reporting an error, and the predicate then matches no version at all. Use a space.

**A missing upper bound is not caught by MenuKit.** MenuKit declares `breaks` against consumer versions known to be incompatible, but it can only name versions that existed when it shipped. Nothing on the MenuKit side can protect your mod from a MenuKit released after it. The upper bound is yours to set.

## When MenuKit takes a new major

Update your code for the new API, change both bounds to the new major, and release under a new version of your own. Do not ship different requirements under a version number you have already published: the two jars claim to be the same release and only one of them works.
