/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package androidx.media3.transformer;

import static androidx.media3.transformer.AndroidTestUtil.BT601_MP4_ASSET_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.BT601_MP4_ASSET_FRAME_COUNT;
import static androidx.media3.transformer.AndroidTestUtil.BT601_MP4_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.JPG_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_FRAME_COUNT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.PNG_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.effect.Presentation;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * End-to-end instrumentation test for {@link Transformer} for cases that cannot be tested using
 * robolectric.
 *
 * <p>This test aims at testing input of {@linkplain VideoFrameProcessor.InputType mixed types of
 * input}.
 */
@RunWith(AndroidJUnit4.class)
public class TransformerMixedInputEndToEndTest {

  // Image by default are encoded in H265 and BT709 SDR.
  private static final Format IMAGE_VIDEO_ENCODING_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H265)
          .setFrameRate(30.f)
          .setWidth(480)
          .setHeight(360)
          .setColorInfo(ColorInfo.SDR_BT709_LIMITED)
          .build();

  private final Context context = ApplicationProvider.getApplicationContext();
  @Rule public final TestName testName = new TestName();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  // TODO: b/343362776 - Add tests to assert enough silence is generated.

  @Test
  public void videoEditing_withImageThenVideoInputs_completesWithCorrectFrameCount()
      throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ IMAGE_VIDEO_ENCODING_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();

    int imageFrameCount = 31;
    EditedMediaItem imageEditedMediaItem =
        createImageEditedMediaItem(PNG_ASSET_URI_STRING, /* frameCount= */ imageFrameCount);
    EditedMediaItem videoEditedMediaItem =
        createVideoEditedMediaItem(MP4_ASSET_URI_STRING, /* height= */ 360);

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, buildComposition(imageEditedMediaItem, videoEditedMediaItem));

    assertThat(result.exportResult.videoFrameCount)
        .isEqualTo(imageFrameCount + MP4_ASSET_FRAME_COUNT);
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void videoEditing_withVideoThenImageInputs_completesWithCorrectFrameCount()
      throws Exception {
    assumeFormatsSupported(
        context, testId, /* inputFormat= */ MP4_ASSET_FORMAT, /* outputFormat= */ MP4_ASSET_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();

    int imageFrameCount = 32;
    EditedMediaItem imageEditedMediaItem =
        createImageEditedMediaItem(PNG_ASSET_URI_STRING, /* frameCount= */ imageFrameCount);
    EditedMediaItem videoEditedMediaItem =
        createVideoEditedMediaItem(MP4_ASSET_URI_STRING, /* height= */ 480);
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, buildComposition(videoEditedMediaItem, imageEditedMediaItem));

    assertThat(result.exportResult.videoFrameCount)
        .isEqualTo(imageFrameCount + MP4_ASSET_FRAME_COUNT);
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void videoEditing_withShortAlternatingImages_completesWithCorrectFrameCount()
      throws Exception {
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();

    EditedMediaItem image1 =
        new EditedMediaItem.Builder(MediaItem.fromUri(PNG_ASSET_URI_STRING))
            .setDurationUs(100_000)
            .setFrameRate(30)
            .build();
    int image1FrameCount = 3;
    EditedMediaItem image2 =
        new EditedMediaItem.Builder(MediaItem.fromUri(JPG_ASSET_URI_STRING))
            .setDurationUs(200_000)
            .setFrameRate(30)
            .build();
    int image2FrameCount = 6;

    ArrayList<EditedMediaItem> editedMediaItems = new ArrayList<>(100);
    for (int i = 0; i < 50; i++) {
      editedMediaItems.add(image1);
      editedMediaItems.add(image2);
    }

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, buildComposition(editedMediaItems));

    // TODO: b/346289922 - Check frame count with extractors.
    assertThat(result.exportResult.videoFrameCount)
        .isEqualTo(50 * image1FrameCount + 50 * image2FrameCount);
    // 50 100ms-images and 50 200ms-images
    assertThat(result.exportResult.durationMs).isEqualTo(14_966);
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void
      videoEditing_withComplexMixedColorSpaceSdrVideoAndImageInputsEndWithVideo_completesWithCorrectFrameCount()
          throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ BT601_MP4_ASSET_FORMAT);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ BT601_MP4_ASSET_FORMAT,
        /* outputFormat= */ BT601_MP4_ASSET_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();

    int imageFrameCount = 33;
    EditedMediaItem imageEditedMediaItem1 =
        createImageEditedMediaItem(PNG_ASSET_URI_STRING, /* frameCount= */ imageFrameCount);
    EditedMediaItem imageEditedMediaItem2 =
        createImageEditedMediaItem(JPG_ASSET_URI_STRING, /* frameCount= */ imageFrameCount);
    EditedMediaItem bt601VideoEditedMediaItem =
        createVideoEditedMediaItem(BT601_MP4_ASSET_URI_STRING, /* height= */ 360);
    EditedMediaItem bt709VideoEditedMediaItem =
        createVideoEditedMediaItem(MP4_ASSET_URI_STRING, /* height= */ 360);
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(
                testId,
                buildComposition(
                    bt601VideoEditedMediaItem,
                    bt709VideoEditedMediaItem,
                    imageEditedMediaItem1,
                    imageEditedMediaItem2,
                    bt709VideoEditedMediaItem,
                    imageEditedMediaItem1,
                    bt601VideoEditedMediaItem));

    assertThat(result.exportResult.videoFrameCount)
        .isEqualTo(
            3 * imageFrameCount + 2 * MP4_ASSET_FRAME_COUNT + 2 * BT601_MP4_ASSET_FRAME_COUNT);
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void
      videoEditing_withComplexMixedColorSpaceSdrVideoAndImageInputsEndWithImage_completesWithCorrectFrameCount()
          throws Exception {

    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ IMAGE_VIDEO_ENCODING_FORMAT);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ BT601_MP4_ASSET_FORMAT,
        /* outputFormat= */ IMAGE_VIDEO_ENCODING_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();

    int imageFrameCount = 34;
    EditedMediaItem imageEditedMediaItem =
        createImageEditedMediaItem(PNG_ASSET_URI_STRING, /* frameCount= */ imageFrameCount);
    EditedMediaItem bt601VideoEditedMediaItem =
        createVideoEditedMediaItem(BT601_MP4_ASSET_URI_STRING, /* height= */ 480);
    EditedMediaItem bt709VideoEditedMediaItem =
        createVideoEditedMediaItem(MP4_ASSET_URI_STRING, /* height= */ 480);
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(
                testId,
                buildComposition(
                    imageEditedMediaItem,
                    bt709VideoEditedMediaItem,
                    bt601VideoEditedMediaItem,
                    imageEditedMediaItem,
                    imageEditedMediaItem,
                    bt601VideoEditedMediaItem,
                    imageEditedMediaItem));

    assertThat(result.exportResult.videoFrameCount)
        .isEqualTo(4 * imageFrameCount + MP4_ASSET_FRAME_COUNT + 2 * BT601_MP4_ASSET_FRAME_COUNT);
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void
      videoEditing_withComplexVideoAndImageInputsEndWithVideo_completesWithCorrectFrameCount()
          throws Exception {
    assumeFormatsSupported(
        context, testId, /* inputFormat= */ MP4_ASSET_FORMAT, /* outputFormat= */ MP4_ASSET_FORMAT);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ BT601_MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();

    int imageFrameCount = 33;
    EditedMediaItem imageEditedMediaItem1 =
        createImageEditedMediaItem(PNG_ASSET_URI_STRING, /* frameCount= */ imageFrameCount);
    EditedMediaItem imageEditedMediaItem2 =
        createImageEditedMediaItem(JPG_ASSET_URI_STRING, /* frameCount= */ imageFrameCount);
    EditedMediaItem videoEditedMediaItem1 =
        createVideoEditedMediaItem(MP4_ASSET_URI_STRING, /* height= */ 360);
    EditedMediaItem videoEditedMediaItem2 =
        createVideoEditedMediaItem(BT601_MP4_ASSET_URI_STRING, /* height= */ 360);
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(
                testId,
                buildComposition(
                    videoEditedMediaItem1,
                    videoEditedMediaItem2,
                    imageEditedMediaItem1,
                    imageEditedMediaItem2,
                    videoEditedMediaItem1,
                    imageEditedMediaItem1,
                    videoEditedMediaItem2));

    assertThat(result.exportResult.videoFrameCount)
        .isEqualTo(
            3 * imageFrameCount + 2 * MP4_ASSET_FRAME_COUNT + 2 * BT601_MP4_ASSET_FRAME_COUNT);
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void
      videoEditing_withComplexVideoAndImageInputsEndWithImage_completesWithCorrectFrameCount()
          throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ IMAGE_VIDEO_ENCODING_FORMAT);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ BT601_MP4_ASSET_FORMAT,
        /* outputFormat= */ IMAGE_VIDEO_ENCODING_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();

    int imageFrameCount = 34;
    EditedMediaItem imageEditedMediaItem =
        createImageEditedMediaItem(PNG_ASSET_URI_STRING, /* frameCount= */ imageFrameCount);
    EditedMediaItem videoEditedMediaItem1 =
        createVideoEditedMediaItem(MP4_ASSET_URI_STRING, /* height= */ 480);
    EditedMediaItem videoEditedMediaItem2 =
        createVideoEditedMediaItem(BT601_MP4_ASSET_URI_STRING, /* height= */ 480);
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(
                testId,
                buildComposition(
                    imageEditedMediaItem,
                    videoEditedMediaItem1,
                    videoEditedMediaItem2,
                    imageEditedMediaItem,
                    imageEditedMediaItem,
                    videoEditedMediaItem2,
                    imageEditedMediaItem));

    assertThat(result.exportResult.videoFrameCount)
        .isEqualTo(4 * imageFrameCount + MP4_ASSET_FRAME_COUNT + 2 * BT601_MP4_ASSET_FRAME_COUNT);
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  /** Creates an {@link EditedMediaItem} with image, with duration of one second. */
  private static EditedMediaItem createImageEditedMediaItem(String uri, int frameCount) {
    return new EditedMediaItem.Builder(MediaItem.fromUri(uri))
        .setDurationUs(C.MICROS_PER_SECOND)
        .setFrameRate(frameCount)
        .build();
  }

  /**
   * Creates an {@link EditedMediaItem} with video, with audio removed and a {@link Presentation} of
   * specified {@code height}.
   */
  private static EditedMediaItem createVideoEditedMediaItem(String uri, int height) {
    return new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(uri)))
        .setEffects(
            new Effects(
                /* audioProcessors= */ ImmutableList.of(),
                ImmutableList.of(Presentation.createForHeight(height))))
        .setRemoveAudio(true)
        .build();
  }

  private static Composition buildComposition(List<EditedMediaItem> editedMediaItems) {
    return new Composition.Builder(new EditedMediaItemSequence(editedMediaItems))
        .setEffects(
            new Effects(
                /* audioProcessors= */ ImmutableList.of(),
                ImmutableList.of(
                    // To ensure that software encoders can encode.
                    Presentation.createForWidthAndHeight(
                        /* width= */ 480, /* height= */ 360, Presentation.LAYOUT_SCALE_TO_FIT))))
        .build();
  }

  private static Composition buildComposition(
      EditedMediaItem editedMediaItem, EditedMediaItem... editedMediaItems) {
    return buildComposition(
        new ImmutableList.Builder<EditedMediaItem>()
            .add(editedMediaItem)
            .add(editedMediaItems)
            .build());
  }
}
