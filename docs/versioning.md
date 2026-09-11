# Versioning

MenuKit follows semantic versioning. The major number is the compatibility contract.

## What each part means

A **major** bump means something public was removed or renamed. Code built against the previous
major may not link. 4.0.0 removed several rendering helpers; 5.0.0 replaced the four region
enums with `OutsideRegion` and `InsideRegion`, and the two bounds records with `Reference`.

A **minor** bump adds API. Code built against an earlier minor of the same major keeps working.

A **patch** bump fixes behaviour without changing the API.

The `+26.2` suffix is the Minecraft target. It is build metadata and takes no part in version
comparison, so a range written against `5.0.0` still matches `5.0.0+26.3` when MenuKit is
rebuilt for a newer Minecraft.

## What to declare

Pin to the major.minor you built against, not just the major:

```json
"depends": { "menukit": ">=5.1.0 <6.0.0" }
```

A minor release can add API. MenuKit 5.1.0 added `SlotOperations`, `SlotGroups`, and
`SlotGroupSet`. If your mod calls those and only declares `">=5.0.0"`, it loads fine on
MenuKit 5.0.0 and crashes with `NoSuchMethodError` the first time it calls something that
does not exist yet at that version. Semantic versioning only promises new API moving forward
within a major, so your floor has to track the minor you actually used.

If you also use MenuKit: Containers, pin it the same way, to the minor you built against.
Containers and MenuKit are released together and always carry the same version.

```json
"depends": {
  "menukit": ">=5.1.0 <6.0.0",
  "menukit-containers": ">=5.1.0 <6.0.0"
}
```

Set the lower bound to the version you actually built and tested against, not the oldest one
you believe still works. An untested lower bound is a claim you cannot support, and it holds
only until the day you call something newer.

## Two things that will bite you

**A comma between the bounds silently disables your mod.** Fabric parses `">=5.0.0,<6.0.0"`
without reporting an error, and the predicate then matches no version at all. Use a space.

**A missing upper bound is not caught by MenuKit.** MenuKit declares `breaks` against consumer
versions known to be incompatible, but it can only name versions that existed when it shipped.
Nothing on the MenuKit side can protect your mod from a MenuKit released after it. The upper
bound is yours to set.

## When MenuKit takes a new major

Update your code for the new API, change both bounds to the new major, and release under a new
version of your own. Do not ship different requirements under a version number you have already
published: the two jars claim to be the same release and only one of them works.
