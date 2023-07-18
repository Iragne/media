/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.extractor.text;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.text.dvb.DvbParser;
import androidx.media3.extractor.text.pgs.PgsParser;
import androidx.media3.extractor.text.ssa.SsaParser;
import androidx.media3.extractor.text.subrip.SubripParser;
import androidx.media3.extractor.text.tx3g.Tx3gParser;
import androidx.media3.extractor.text.webvtt.Mp4WebvttParser;
import java.util.Objects;

/**
 * A factory for {@link SubtitleParser} instances.
 *
 * <p>The formats supported by this factory are:
 *
 * <ul>
 *   <li>WebVTT (MP4) ({@link Mp4WebvttParser})
 *   <li>SubRip ({@link SubripParser})
 *   <li>SSA/ASS ({@link SsaParser})
 *   <li>TX3G ({@link Tx3gParser})
 *   <li>PGS ({@link PgsParser})
 *   <li>DVB ({@link DvbParser})
 * </ul>
 */
@UnstableApi
public final class DefaultSubtitleParserFactory implements SubtitleParser.Factory {

  @Override
  public boolean supportsFormat(Format format) {
    @Nullable String mimeType = format.sampleMimeType;
    return Objects.equals(mimeType, MimeTypes.TEXT_SSA)
        || Objects.equals(mimeType, MimeTypes.APPLICATION_MP4VTT)
        || Objects.equals(mimeType, MimeTypes.APPLICATION_SUBRIP)
        || Objects.equals(mimeType, MimeTypes.APPLICATION_TX3G)
        || Objects.equals(mimeType, MimeTypes.APPLICATION_PGS)
        || Objects.equals(mimeType, MimeTypes.APPLICATION_DVBSUBS);
  }

  @Override
  public SubtitleParser create(Format format) {
    @Nullable String mimeType = format.sampleMimeType;
    if (mimeType != null) {
      switch (mimeType) {
        case MimeTypes.TEXT_SSA:
          return new SsaParser(format.initializationData);
        case MimeTypes.APPLICATION_MP4VTT:
          return new Mp4WebvttParser();
        case MimeTypes.APPLICATION_SUBRIP:
          return new SubripParser();
        case MimeTypes.APPLICATION_TX3G:
          return new Tx3gParser(format.initializationData);
        case MimeTypes.APPLICATION_PGS:
          return new PgsParser();
        case MimeTypes.APPLICATION_DVBSUBS:
          return new DvbParser(format.initializationData);
        default:
          break;
      }
    }
    throw new IllegalArgumentException(
        "Attempted to create parser for unsupported MIME type: " + mimeType);
  }
}