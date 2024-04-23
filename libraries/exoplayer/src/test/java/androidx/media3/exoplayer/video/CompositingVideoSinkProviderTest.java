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
 */
package androidx.media3.exoplayer.video;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.PreviewingVideoGraph;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.VideoGraph;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/** Unit test for {@link CompositingVideoSinkProvider}. */
@RunWith(AndroidJUnit4.class)
public final class CompositingVideoSinkProviderTest {

  @Test
  public void builder_calledMultipleTimes_throws() {
    CompositingVideoSinkProvider.Builder builder =
        new CompositingVideoSinkProvider.Builder(ApplicationProvider.getApplicationContext());

    builder.build();

    assertThrows(IllegalStateException.class, builder::build);
  }

  @Test
  public void initializeSink_withoutReleaseControl_throws() {
    CompositingVideoSinkProvider provider =
        new CompositingVideoSinkProvider.Builder(ApplicationProvider.getApplicationContext())
            .setPreviewingVideoGraphFactory(new TestPreviewingVideoGraphFactory())
            .build();
    VideoSink sink = provider.getSink();

    assertThrows(
        IllegalStateException.class,
        () -> sink.initialize(new Format.Builder().setWidth(640).setHeight(480).build()));
  }

  @Test
  public void initializeSink_calledTwice_throws() throws VideoSink.VideoSinkException {
    CompositingVideoSinkProvider provider = createCompositingVideoSinkProvider();
    VideoSink sink = provider.getSink();
    sink.initialize(new Format.Builder().build());

    assertThrows(IllegalStateException.class, () -> sink.initialize(new Format.Builder().build()));
  }

  @Test
  public void setOutputStreamOffsetUs_frameReleaseTimesAreAdjusted()
      throws VideoSink.VideoSinkException {
    CompositingVideoSinkProvider provider = createCompositingVideoSinkProvider();
    VideoSink videoSink = provider.getSink();
    videoSink.initialize(new Format.Builder().build());
    videoSink.registerInputStream(
        VideoSink.INPUT_TYPE_SURFACE, new Format.Builder().setWidth(640).setHeight(480).build());

    assertThat(videoSink.registerInputFrame(/* framePresentationTimeUs= */ 0, false)).isEqualTo(0);
    provider.setStreamOffsetUs(1_000);
    assertThat(videoSink.registerInputFrame(/* framePresentationTimeUs= */ 0, false))
        .isEqualTo(1_000_000);
    provider.setStreamOffsetUs(2_000);
    assertThat(videoSink.registerInputFrame(/* framePresentationTimeUs= */ 0, false))
        .isEqualTo(2_000_000);
  }

  private static CompositingVideoSinkProvider createCompositingVideoSinkProvider() {
    Context context = ApplicationProvider.getApplicationContext();
    VideoFrameReleaseControl.FrameTimingEvaluator frameTimingEvaluator =
        new VideoFrameReleaseControl.FrameTimingEvaluator() {
          @Override
          public boolean shouldForceReleaseFrame(long earlyUs, long elapsedSinceLastReleaseUs) {
            return false;
          }

          @Override
          public boolean shouldDropFrame(
              long earlyUs, long elapsedRealtimeUs, boolean isLastFrame) {
            return false;
          }

          @Override
          public boolean shouldIgnoreFrame(
              long earlyUs,
              long positionUs,
              long elapsedRealtimeUs,
              boolean isLastFrame,
              boolean treatDroppedBuffersAsSkipped) {
            return false;
          }
        };
    CompositingVideoSinkProvider compositingVideoSinkProvider =
        new CompositingVideoSinkProvider.Builder(context)
            .setPreviewingVideoGraphFactory(new TestPreviewingVideoGraphFactory())
            .build();
    compositingVideoSinkProvider.setVideoFrameReleaseControl(
        new VideoFrameReleaseControl(context, frameTimingEvaluator, /* allowedJoiningTimeMs= */ 0));
    return compositingVideoSinkProvider;
  }

  private static class TestPreviewingVideoGraphFactory implements PreviewingVideoGraph.Factory {
    // Using a mock but we don't assert mock interactions. If needed to assert interactions, we
    // should a fake instead.
    private final PreviewingVideoGraph previewingVideoGraph =
        Mockito.mock(PreviewingVideoGraph.class);
    private final VideoFrameProcessor videoFrameProcessor = Mockito.mock(VideoFrameProcessor.class);

    @Override
    public PreviewingVideoGraph create(
        Context context,
        ColorInfo outputColorInfo,
        DebugViewProvider debugViewProvider,
        VideoGraph.Listener listener,
        Executor listenerExecutor,
        List<Effect> compositionEffects,
        long initialTimestampOffsetUs) {
      when(previewingVideoGraph.getProcessor(anyInt())).thenReturn(videoFrameProcessor);
      when(videoFrameProcessor.registerInputFrame()).thenReturn(true);
      return previewingVideoGraph;
    }
  }
}
