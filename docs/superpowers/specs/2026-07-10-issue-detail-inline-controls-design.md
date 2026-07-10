# Issue Detail Inline Controls Design

## Scope

Refactor `mobile/src/views/cps/IssueDetailView.vue` so it no longer imports or renders the CPS-specific `ActionPanel`, `FlowTimeline`, or `ImageUploader` components. The page must remain compatible with the Vant 3 component subset, regardless of the current package manifest declaration.

## Approach

Keep the existing API calls, action payload shape, status-driven form visibility, and photo-preview behavior. Move the three presentation concerns into the page:

- Render available workflow actions as native buttons, with the existing action labels and review-state variants.
- Render flow logs with a local ordered list and empty-state markup.
- Reuse the create page's `uni.chooseImage`, upload, deletion, and `uni.previewImage` pattern for proof images.

## Vant Compatibility

Only retain the existing, basic Vant form primitives already used by the page (`van-cell-group`, `van-field`, and `van-loading`). Do not add Vant 4-only APIs or component imports. Image preview will use `uni.previewImage` so it does not depend on Vant's preview API.

## Validation

Update the detail-view unit test to assert that the inline actions and timeline render, and that proof image selection/upload behavior is independently testable. Run the focused Vitest test and the mobile TypeScript check.
