import { mount } from '@vue/test-utils'

import ImageUploader from '../ImageUploader.vue'

const chooseImage = vi.fn()
const previewImage = vi.fn()

describe('ImageUploader', () => {
  beforeEach(() => {
    chooseImage.mockReset()
    previewImage.mockReset()
    Object.defineProperty(globalThis, 'uni', {
      configurable: true,
      value: {
        chooseImage,
        previewImage,
      },
    })
  })

  it('uses uni.chooseImage and uploads selected temporary paths', async () => {
    const upload = vi
      .fn()
      .mockResolvedValueOnce({ id: 1, url: '/a.jpg', name: 'a.jpg' })
      .mockResolvedValueOnce({ id: 2, url: '/b.jpg', name: 'b.jpg' })
    chooseImage.mockImplementation((options) => {
      options.success({
        tempFilePaths: ['tmp-a.jpg', 'tmp-b.jpg'],
        tempFiles: [{ path: 'tmp-a.jpg' }, { path: 'tmp-b.jpg' }],
      })
    })

    const wrapper = mount(ImageUploader, {
      props: {
        modelValue: [],
        upload,
        'onUpdate:modelValue': (value) => wrapper.setProps({ modelValue: value }),
      },
    })

    await (
      wrapper.vm as unknown as {
        chooseAndUpload: () => Promise<void>
      }
    ).chooseAndUpload()

    expect(chooseImage).toHaveBeenCalledWith(
      expect.objectContaining({
        count: 5,
        sourceType: ['album', 'camera'],
      }),
    )
    expect(upload).toHaveBeenNthCalledWith(1, 'tmp-a.jpg')
    expect(upload).toHaveBeenNthCalledWith(2, 'tmp-b.jpg')
    expect(wrapper.emitted('firstImageReady')).toEqual([[1]])
    expect(wrapper.props('modelValue')).toHaveLength(2)
  })
})
