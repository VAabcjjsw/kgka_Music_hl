import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../models/music_models.dart';

/// SuperLyric 逐字数据，用于卡拉OK风格。
class SuperLyricWordData {
  const SuperLyricWordData({
    required this.word,
    required this.startTime,
    required this.endTime,
  });

  final String word;
  final Duration startTime;
  final Duration endTime;

  Map<String, dynamic> toMap() => {
        'word': word,
        'startTime': startTime.inMilliseconds,
        'endTime': endTime.inMilliseconds,
      };
}

/// SuperLyric 系统级歌词发布服务。
///
/// 通过 Android SuperLyricApi（基于 Binder/AIDL）将实时歌词数据
/// 发布到系统级服务，供 Xposed 模块等接收方使用。
///
/// 仅在 Android 平台生效，非 Android 平台的所有操作均为安全的 no-op。
class SuperLyricService {
  static const _channel = MethodChannel('kgka_music_hl/super_lyric');

  bool _registered = false;

  /// 是否为支持的平台（仅 Android）。
  static bool get isSupportedPlatform {
    return !kIsWeb && defaultTargetPlatform == TargetPlatform.android;
  }

  /// 检查 SuperLyric 系统服务是否可用（即 SuperLyric Xposed 模块是否安装并激活）。
  Future<bool> isAvailable() async {
    if (!isSupportedPlatform) return false;
    try {
      final result = await _channel.invokeMethod<bool>('isAvailable');
      return result ?? false;
    } on MissingPluginException {
      return false;
    } catch (_) {
      return false;
    }
  }

  /// 注册为 SuperLyric 发布者（应用启动时调用一次即可）。
  ///
  /// 返回是否注册成功。未安装 SuperLyric 模块时返回 false，不会抛错。
  Future<bool> registerPublisher() async {
    if (!isSupportedPlatform) return false;
    if (_registered) return true;
    try {
      final result = await _channel.invokeMethod<bool>('registerPublisher');
      final ok = result ?? false;
      if (ok) {
        _registered = true;
      }
      return ok;
    } on MissingPluginException {
      return false;
    } catch (_) {
      return false;
    }
  }

  /// 主动取消注册（应用销毁时通常由系统自动清理）。
  Future<void> unregisterPublisher() async {
    if (!isSupportedPlatform || !_registered) return;
    try {
      await _channel.invokeMethod<void>('unregisterPublisher');
      _registered = false;
    } on MissingPluginException {
      // ignore
    } catch (_) {
      // ignore
    }
  }

  /// 发送当前歌词行到系统服务。
  ///
  /// - [song] 当前歌曲信息（标题、艺人、专辑等）
  /// - [line] 当前歌词行（含时间、文本、翻译、罗马音、逐字数据）
  /// - [lineEndTime] 当前行的预计结束时间，用于计算 SuperLyricLine 的 endTime；
  ///   不传时使用行内 duration 或退化为行开始时间
  Future<void> sendLyric({
    required Song song,
    required LyricLine line,
    Duration? lineEndTime,
  }) async {
    if (!isSupportedPlatform || !_registered) return;
    final int endMs;
    if (lineEndTime != null) {
      endMs = lineEndTime.inMilliseconds;
    } else if (line.duration != null) {
      endMs = line.time.inMilliseconds + line.duration!.inMilliseconds;
    } else {
      endMs = line.time.inMilliseconds;
    }

    final List<Map<String, dynamic>> words;
    if (line.words.isNotEmpty) {
      words = line.words.map((w) {
        final start = line.time.inMilliseconds + w.time.inMilliseconds;
        return SuperLyricWordData(
          word: w.text,
          startTime: Duration(milliseconds: start),
          endTime: Duration(milliseconds: start + w.duration.inMilliseconds),
        ).toMap();
      }).toList();
    } else {
      words = const [];
    }

    try {
      await _channel.invokeMethod<void>('sendLyric', {
        'title': song.title,
        'artist': song.artist,
        'album': song.albumName ?? '',
        'lyricText': line.text,
        'lyricStartTime': line.time.inMilliseconds,
        'lyricEndTime': endMs,
        'secondaryText': line.romanization,
        'translationText': line.translation,
        'words': words,
      });
    } on MissingPluginException {
      // ignore
    } catch (_) {
      // ignore: 失败不影响正常播放
    }
  }

  /// 发送播放停止/暂停事件。
  Future<void> sendStop() async {
    if (!isSupportedPlatform || !_registered) return;
    try {
      await _channel.invokeMethod<void>('sendStop');
    } on MissingPluginException {
      // ignore
    } catch (_) {
      // ignore
    }
  }
}
