# Issue Detail Inline Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the CPS issue detail page self-contained by inlining workflow actions, the flow timeline, and proof-image uploading while staying within the Vant 3-compatible control set.

**Architecture:** `IssueDetailView.vue` will own the labels and rendering previously encapsulated by the three CPS presentation components. The existing API calls and action payload remain unchanged. Proof-image selection and upload will follow `IssueCreateView.vue`, using `uni.chooseImage`, `uploadCpsAttachment`, and `uni.previewImage`.

**Tech Stack:** Vue 3 Composition API, TypeScript, uni-app, Vant 3-compatible `van-cell-group`/`van-field`/`van-loading`, Vitest, Vue Test Utils.

---

### Task 1: Capture Inline Detail-View Behavior in Tests

**Files:**
- Modify: `mobile/src/views/cps/__tests__/IssueDetailView.spec.ts`
- Test: `mobile/src/views/cps/__tests__/IssueDetailView.spec.ts`

- [ ] **Step 1: Write the failing test for inline action controls and timeline labels**

Replace the component stubs and add this test after the existing status test:

```ts
it('renders available actions and the timeline without CPS child components', async () => {
  const wrapper = mount(IssueDetailView)

  await flushPromises()

  expect(wrapper.findComponent({ name: 'ActionPanel' }).exists()).toBe(false)
  expect(wrapper.findComponent({ name: 'FlowTimeline' }).exists()).toBe(false)
  expect(wrapper.text()).toContain('审核关闭')
  expect(wrapper.text()).toContain('审核退回')
  expect(wrapper.text()).toContain('提交问题')
  expect(wrapper.text()).toContain('待反馈 至 待审核')
})
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `pnpm.cmd test src/views/cps/__tests__/IssueDetailView.spec.ts`

Expected: FAIL because `ActionPanel` and `FlowTimeline` are still imported and rendered by the page.

- [ ] **Step 3: Write the failing test for inline proof-image selection/upload**

Extend the `uni-app` mock to provide `chooseImage`, mock `uploadCpsAttachment`, and add:

```ts
it('uploads proof images from the inline upload control', async () => {
  mocks.uploadCpsAttachment.mockResolvedValue({ id: 701, url: '/proof-2.jpg', name: '整改凭证-2.jpg' })
  mocks.chooseImage.mockImplementation(({ success }) => success({ tempFilePaths: ['/proof-2.jpg'], tempFiles: [] }))
  const wrapper = mount(IssueDetailView)

  await flushPromises()
  await wrapper.get('[data-test="proof-upload"]').trigger('click')
  await flushPromises()

  expect(mocks.uploadCpsAttachment).toHaveBeenCalledWith('/proof-2.jpg')
  expect(wrapper.findAll('.cps-proof-uploader__preview')).toHaveLength(2)
})
```

- [ ] **Step 4: Run the focused test to verify it fails**

Run: `pnpm.cmd test src/views/cps/__tests__/IssueDetailView.spec.ts`

Expected: FAIL because the page has no `[data-test="proof-upload"]` inline uploader control.

### Task 2: Inline Actions, Timeline, and Proof Uploading

**Files:**
- Modify: `mobile/src/views/cps/IssueDetailView.vue`
- Test: `mobile/src/views/cps/__tests__/IssueDetailView.spec.ts`

- [ ] **Step 1: Replace component imports with the local upload dependency and label maps**

Remove:

```ts
import ActionPanel from '@/components/cps/ActionPanel.vue'
import FlowTimeline from '@/components/cps/FlowTimeline.vue'
import ImageUploader from '@/components/cps/ImageUploader.vue'
```

Add:

```ts
import { uploadCpsAttachment, type CpsAttachmentUploadSource } from '@/api/cps/attachment'
```

Define local `actionLabels` and `statusLabels` records with the values currently held by `ActionPanel.vue` and `FlowTimeline.vue`, plus `actionLabel` and `flowStatusLabel` helpers. Define `proofUploading`, `UniTempFileLike`, and `TempFileCandidate` alongside the current form state.

- [ ] **Step 2: Replace the proof uploader template with inline markup**

Replace the `ImageUploader` usage with a grid that uses `proofImages`, supports preview and deletion, and exposes this upload control:

```vue
<button
  v-if="proofImages.length < 5"
  data-test="proof-upload"
  type="button"
  class="cps-proof-uploader__upload"
  :disabled="proofUploading"
  @click="chooseAndUploadProofImages"
>
  <span class="cps-proof-uploader__plus">+</span>
  <span>{{ proofUploading ? '上传中' : '上传整改图片' }}</span>
</button>
```

- [ ] **Step 3: Implement proof upload and preview functions**

Implement `chooseAndUploadProofImages`, `normalizeArray`, `resolveUploadSources`, `removeProofImage`, and `previewProofImage` using the same `uni.chooseImage` inputs as `IssueCreateView.vue`. Upload no more than the remaining capacity, append successful uploads to `proofImages`, ignore cancelled selection, and show `uni.showToast({ title: '图片选择失败', icon: 'none' })` for selection errors.

- [ ] **Step 4: Replace action and timeline component template usage**

Replace the `ActionPanel` with native buttons rendered from `detail.availableActions` and bound to `runAction`. Replace `FlowTimeline` with an inline empty state or an `<ol>` that renders each flow log, its localized action, localized from/to statuses, operator, timestamp, and optional comment.

- [ ] **Step 5: Add local scoped styles**

Add styles for `.cps-proof-uploader*`, `.cps-inline-action-panel*`, and `.cps-flow-timeline*`, copying the dimensions and visual treatment of the removed child components. Use native controls and avoid Vant components beyond the existing basic form primitives.

- [ ] **Step 6: Run the focused tests to verify they pass**

Run: `pnpm.cmd test src/views/cps/__tests__/IssueDetailView.spec.ts`

Expected: PASS with all detail-view behavior tests green.

### Task 3: Verify the Mobile Project

**Files:**
- Modify: `mobile/src/views/cps/IssueDetailView.vue`
- Modify: `mobile/src/views/cps/__tests__/IssueDetailView.spec.ts`

- [ ] **Step 1: Run the detail-view test suite**

Run: `pnpm.cmd test src/views/cps/__tests__/IssueDetailView.spec.ts`

Expected: PASS.

- [ ] **Step 2: Run TypeScript validation**

Run: `pnpm.cmd typecheck`

Expected: PASS with no Vue or TypeScript diagnostics.

- [ ] **Step 3: Inspect the final diff and commit the implementation**

Run: `git -c safe.directory=D:/Develop/cps diff --check` followed by `git -c safe.directory=D:/Develop/cps status --short`.

Expected: no whitespace errors; only `IssueDetailView.vue` and `IssueDetailView.spec.ts` changed for the implementation.

Commit:

```bash
git add mobile/src/views/cps/IssueDetailView.vue mobile/src/views/cps/__tests__/IssueDetailView.spec.ts
git commit -m "refactor: inline issue detail controls"
```
