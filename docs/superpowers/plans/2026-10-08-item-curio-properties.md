# Item and Accessory Properties Implementation Plan

> Execute inline using superpowers:executing-plans. Do not delegate.

**Goal:** Apply saved item changes safely and integrate optional Curios conversion in the existing editor.

**Architecture:** Immutable config snapshots, one-time equipment refresh on save, optional Curios events, existing Kinetic UI canvas.

**Tech Stack:** Stonecutter, MDG, Java 17/21/25, Curios 5/9/15.

**Spec:** ../specs/2026-10-08-item-curio-properties-design.md

## Constraints and review focus

- Preserve three-version layout and existing features; fixed widths and at least 2 px gaps.
- Curios must remain optional; absent dependency cannot load adapter classes.
- Reject invalid known rules before persisting; preserve unresolved rows.
- Saving/resetting equipped items must not accumulate stale attribute modifiers.
- Any-slot conversion cannot create slots or overwrite another mod's item callbacks.
- 26.1.2 default-component limitations must be accurately documented.

## Tasks

- [x] Add failing Curio settings validation regression tests, then implement bounded schema and optional dependency declarations.
- [x] Add runtime tests for equipped armor, held damage/durability and reset, then implement save-time snapshot activation and equipment refresh.
- [x] Add Curios event integration and runtime cases for multiple/any slots, removal lock and attribute cleanup.
- [x] Extend the existing category panel and slot picker; verify missing-dependency state and EN/ZH layout at small/large sizes.
- [x] Build all three real release JARs, update CHANGELOG/README and separate bilingual Wiki pages, and review the diff.

Publication procedure: commit and push the source and Wiki, then verify the exact source commit's GitHub Actions run and three release assets. Keep the run IDs and artifact hashes in the ignored validation receipt.
