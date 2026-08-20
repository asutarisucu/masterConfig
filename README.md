## MasterConfig

Minecraft の設定・データ類を 1 つの「マスターフォルダ」に集約し、
複数インスタンス／複数バージョンから同じ実体を共有するための Fabric MOD。

コピーして同期するのではなく **実体は 1 つだけ**。どのインスタンスから編集しても全体に反映される。

- 対象: Minecraft 1.18.2 以降（1.18.2 / 1.19.4 / 1.20.1 / 1.20.2 / 1.20.4 / 1.20.6 /
  1.21.1 / 1.21.3 / 1.21.4 / 1.21.5 / 1.21.8 / 1.21.10 / 1.21.11 / 26.1.2 / 26.2）
- 設計と実装状況: [DESIGN.md](DESIGN.md)

### 共有されるもの

| 対象 | 手段 |
|---|---|
| `config/`（他 MOD の設定） | OS のリンク |
| `schematics/` | OS のリンク |
| `resourcepacks/` | OS のリンク |
| `shaderpacks/` | OS のリンク |
| 任意のフォルダ（journeymap など） | OS のリンク、`extraLinks` で指定 |
| `options.txt` | Mixin でパスを差し替え、バージョン間でキーが消えないように保全 |
| ホットバー保存 | 独自の共通形式 `hotbars.dat` に変換して共有 |

`mods/` と `saves/` は対象外。

### 使い方

1. 各インスタンスの `mods/` に jar を入れて一度起動する
2. ゲームディレクトリ直下に `masterconfig.json` ができるので `masterRoot` に共有先のパスを書く
3. もう一度起動すると、既存の内容がマスターフォルダへ移り、リンクに置き換わる

```json
{
  "enabled": true,
  "masterRoot": "D:/minecraft/MasterConfig/master",
  "targets": {
    "config": true,
    "schematics": true,
    "resourcepacks": true,
    "shaderpacks": true,
    "options.txt": true,
    "hotbar.nbt": true
  },
  "extraLinks": [
    { "game": "journeymap", "master": "journeymap" }
  ],
  "preserveUnknownOptions": true,
  "conflictPolicy": "MASTER_WINS",
  "warnOnConcurrentLaunch": true
}
```

- マスターフォルダに既に同名のものがある場合は**マスター側が優先**され、
  ローカルの内容は `masterconfig_backup/<日時>/` へ退避される（削除はしない）。
- 起動しなくなったら `"enabled": false` か JVM 引数 `-Dmasterconfig.disable=true` で全て無効化できる。
- `-Dmasterconfig.root=<path>` でランチャー側から `masterRoot` を上書きできる。

### ビルド

```
./gradlew buildAndGather
```

全バージョンの jar が `build/libs/` に出力される。

### ライセンスと謝辞

LGPL-3.0。テンプレートとして
[Fallen-Breath/fabric-mod-template](https://github.com/Fallen-Breath/fabric-mod-template)
を利用している。
