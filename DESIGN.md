# MasterConfig — 設計書

Minecraft の設定・データ類を 1 つの「マスターフォルダ」に集約し、複数インスタンス／複数バージョンから
同じ実体を共有するための Fabric MOD。

- ベース: [Fallen-Breath/fabric-mod-template](https://github.com/Fallen-Breath/fabric-mod-template)
- ローダー: Fabric / ビルド: Gradle (fabric-loom + ReplayMod preprocessor)
- 対象: Minecraft 1.18 以降のメジャーバージョン全て

---

## 1. 目的と非目標

### 目的

1 台の PC 内に複数の Minecraft インスタンス（バージョン違い含む）がある状況で、
以下を **1 箇所に集約し、各インスタンスからはその実体を直接使う**。

| 対象 | 実体 | 種別 |
|---|---|---|
| MOD の config 置き場 | `config/` | ディレクトリ |
| バニラ設定 | `options.txt` | ファイル |
| クリエイティブのホットバー保存 | `hotbar.nbt` | ファイル（独自共通形式に変換して共有） |
| schematic (Litematica 等) | `schematics/` | ディレクトリ |
| リソースパック | `resourcepacks/` | ディレクトリ |
| シェーダー (Iris / OptiFine) | `shaderpacks/` | ディレクトリ |
| その他 MOD の任意フォルダ／ファイル | config で任意指定 | 両方 |

「コピーして同期する」のではなく **実体は 1 つだけ**。どのインスタンスから編集しても即座に全体へ反映される。

### 非目標

- `mods/` の共有（バージョンごとに必要な jar が違うため。明確に対象外）
- `saves/` の共有（ワールドの同時オープンは破壊的）
- ネットワーク越し／クラウド同期（対象はローカル FS のみ）
- Fabric 以外のローダー対応

---

## 2. 全体方針 — ハイブリッド

種別によって手段を変える。

```
ディレクトリ    →  OS のリンク (symlink → 失敗時ジャンクション)
options.txt     →  Mixin でパス書き換え ＋ 未知キー保全 ＋ DataFixer 抑止
hotbar.nbt      →  独自共通形式 (MCHF) をマスターに置き、起動/保存のたびに変換
リンク不可時    →  config/ のみ FabricLoader.configDir をリフレクションで差し替え（縮退）
```

### なぜ分けるか

**ディレクトリをリンクにする理由**

- 未知の MOD にも 100% 効く。MOD 側が `FabricLoader.getConfigDir()` を使っていようが
  自前で `gameDir/config/foo` を組み立てていようが関係なく効く。
- ゲーム外のツール（Litematica のスキーマ編集ツール、リソパ管理ツール等）からも同じ実体が見える。
- Windows でも **ディレクトリなら権限不要**。symlink が権限で失敗しても
  `mklink /J`（ジャンクション）は非管理者・開発者モード OFF でも作成できる。

**ファイルを Mixin にする理由**

- Windows では**ファイルの symlink は `SeCreateSymbolicLinkPrivilege` が必要**（管理者 or 開発者モード ON）。
  ジャンクションはディレクトリ専用なので代替にならない。ハードリンクは権限不要だが、
  書き手が「一時ファイル＋リネーム」で保存するとリンクが静かに切れるため信頼できない。
- 対象は `options.txt` と `hotbar.nbt` の 2 つだけ。どちらもバニラのクラスにパスを持つフィールドがあり、
  Mixin で確実に差し替えられる。
- そもそもこの 2 つは**単純に共有するだけでは壊れる**（§6・§7）。どのみち Mixin による介入が必要なので、
  パスの解決もそこで一緒にやるのが自然。

> 検証済み: 本開発機では Java の `Files.createSymbolicLink` がファイル・ディレクトリとも成功する
> （開発者モード相当）。ただし配布先はそうとは限らないため、上記フォールバックは必須。

---

## 3. ディレクトリ構成

マスターフォルダ（例 `D:\minecraft\MasterConfig\master`）に**バージョン分割なしで全部入れる**。

```
<master root>/
  config/
  schematics/
  resourcepacks/
  shaderpacks/
  options.txt                 # バニラ互換形式のまま。全バージョンが直接読み書きする
  hotbars.dat                 # MCHF（独自共通形式）。バニラの hotbar.nbt とは別物
  <extraLinks で指定した任意の名前>/
  .masterconfig_lock          # 同時起動検出用
```

各インスタンス側:

```
<gamedir>/
  masterconfig.json           # このMOD自身の設定（リンク対象外・ゲームディレクトリ直下）
  config/         --> symlink/junction --> <master>/config/
  schematics/     --> symlink/junction --> <master>/schematics/
  resourcepacks/  --> symlink/junction --> <master>/resourcepacks/
  shaderpacks/    --> symlink/junction --> <master>/shaderpacks/
  options.txt     ※実体なし。Mixin が <master>/options.txt を直接読み書き
  hotbar.nbt      ※ローカルに残る。ただし中身は毎回 <master>/hotbars.dat から生成される
                    「このバージョン用の作業ファイル」であり、共有はしない
  masterconfig_backup/<yyyyMMdd-HHmmss>/   # 初回移行時の退避先
  mods/  saves/  logs/  ...   # 触らない
```

### このMOD自身の設定を `config/` に置かない理由

`config/` 自体がリンク化の対象なので、「どこにリンクするか」を書いた設定が `config/` の中にあると
鶏と卵になる。よって **`<gamedir>/masterconfig.json`**（ゲームディレクトリ直下）に置く。
初回起動時にデフォルト値で自動生成する。

---

## 4. 設定ファイル `masterconfig.json`

```json
{
  "enabled": true,
  "masterRoot": "D:/minecraft/MasterConfig/master",
  "targets": {
    "config":        true,
    "schematics":    true,
    "resourcepacks": true,
    "shaderpacks":   true,
    "options.txt":   true,
    "hotbar.nbt":    true
  },
  "extraLinks": [
    { "game": "journeymap",     "master": "journeymap" },
    { "game": "XaeroWaypoints", "master": "XaeroWaypoints" }
  ],
  "preserveUnknownOptions": true,
  "conflictPolicy": "MASTER_WINS",
  "warnOnConcurrentLaunch": true
}
```

| キー | 意味 |
|---|---|
| `enabled` | false で MOD 全体を無効化（トラブル時の脱出ハッチ） |
| `masterRoot` | マスターフォルダの絶対パス。空なら MOD は何もせず警告のみ |
| `targets` | 標準対象の個別 ON/OFF |
| `extraLinks` | 任意のフォルダ／ファイルの追加リンク。`game` はゲームディレクトリからの相対、`master` はマスタールートからの相対 |
| `preserveUnknownOptions` | §6 の未知キー保全と DataFixer 抑止の ON/OFF。false でバニラ同等の挙動に戻る |
| `conflictPolicy` | `MASTER_WINS` 固定（将来 `LOCAL_WINS` / `ABORT` を追加可能） |
| `warnOnConcurrentLaunch` | ロックファイルで多重起動を検出して警告 |

パーサは Minecraft 同梱の Gson を使う（追加依存なし）。
`-Dmasterconfig.root=<path>` の JVM 引数があれば `masterRoot` より優先する（ランチャー側から上書きするため）。

---

## 5. 実行フローとフック位置

```
Fabric Loader 起動
  ↓
Mixin 登録・適用（OptionsMixin / HotbarManagerMixin がここで仕込まれる）
  ↓
★ preLaunch entrypoint  ← MasterConfig のリンク処理はここ
  ↓
他 MOD の main/client entrypoint（ここで初めて FabricLoader.getConfigDir() が呼ばれる）
  ↓
Minecraft.<init>  → new Options(...) / new HotbarManager(...)  ← Mixin が発火
```

`preLaunch` を使う理由:

- Fabric Loader は `config/` を **`getConfigDir()` が初めて呼ばれたとき**に遅延生成する。
  それを呼ぶのは各 MOD の初期化＝`preLaunch` より後。よって `preLaunch` の時点なら
  `config/` はまだ存在しないか、存在しても前回起動の残骸である。
- `Options` / `HotbarManager` の生成は `Minecraft.<init>` なのでさらに後。Mixin の発火に十分間に合う。

`fabric.mod.json`:

```json
"environment": "*",
"entrypoints": {
  "preLaunch": ["...MasterConfigPreLaunch"]
}
```

リンク処理自体はサーバー環境でも無害に動く（`config/` の共有はサーバーでも意味がある）。
`options.txt` / `hotbar.nbt` の Mixin は client 側 mixin として分離する。

---

## 6. `options.txt` の未知キー保全

### 問題

バニラの `Options#save()` は **そのバージョンが知っているキーだけ**を書き出す。
1.21 で保存した `options.txt` を 1.18 で開いて保存すると、1.18 が知らないキーは静かに消える。
バージョン分割せず 1 ファイルを共有するなら、ここを直さないと設定が削れていく。

### 解決 — 差分追記方式

`Options#save()` を前後から挟み、**消えたキーを書き戻す**。

```
@Inject(method="save", at=@At("HEAD"))
   before = readKeyValues(optionsFile)        // 保存前のファイル全行を key→value で読む

（バニラの save() が走り、知っているキーだけで上書きされる）

@Inject(method="save", at=@At("RETURN"))
   after   = readKeyValues(optionsFile)
   dropped = before.keySet() - after.keySet() // = このバージョンが知らなかったキー
   dropped があれば末尾に "key:value" を追記
```

- 行の分割は**最初の `:` のみ**（`key_key.attack:key.mouse.left` や
  `resourcePacks:["vanilla","file/foo:bar"]` のように値側に `:` が入るため）。
- バニラは既知キーを常に全て書き出すので、「保存前にあって保存後に無い」＝「未知キー」で一意に判定できる。
  バニラのクラス構造に一切依存しないので、**1.18 〜 26.x まで同一コードで通る**のが最大の利点。
- `save()` は try-with-resources で `PrintWriter` を閉じてから return するため、`RETURN` の時点でファイルは確定している。
- 初回起動（ファイル無し）は `before` が空なので何もしない。
- 副次効果として、別インスタンスが同時に書いたキーもマージされる。

### パス差し替えと初回移行

`Options` は自身の `optionsFile`（`File` 型）フィールドにパスを持ち、コンストラクタ末尾で `load()` を呼ぶ。
`load()` の**呼び出し直前**に `@Inject` して `optionsFile` を `<master>/options.txt` に差し替える。
（`RETURN` では `load()` が済んでしまっているため間に合わない）

`options.txt` はリンクしないので、ディレクトリと違って preLaunch では何も起きない。
そのため既存インスタンスの設定を引き継ぐには、preLaunch で**ファイル単体の移行**を別途行う
（`Linker#migrateFile`）。ルールはディレクトリと同じ MASTER_WINS:
マスター側に無ければ move、あれば `masterconfig_backup/<timestamp>/` へ退避。

### `version` キーと DataFixer の抑止

`options.txt` の `version` キーは **保存時の DataVersion（ワールドデータ形式のバージョン番号）**で、
`Options#save()` が `SharedConstants.getCurrentVersion().getDataVersion().getVersion()` を書き込む。
`Options#load()` はこれを読んで `DataFixTypes.OPTIONS.updateToCurrentVersion(fixer, tag, version)` に渡す。
つまり **options.txt に対する DataFixer の起点バージョン**である。

`DataFixerUpper#update` のバイトコードを確認したところ、先頭に `if (fromVersion >= toVersion) return input;`
に相当する分岐がある。したがって:

- **新しい版で保存 → 古い版で読む**: 何も起きない（no-op）。安全。
- **古い版で保存 → 新しい版で読む**: `fromVersion → toVersion` の OPTIONS 系 fixer が全部適用される。**ここが危険。**

共有ファイルには未知キー保全によって「新しい版が書いたキー」が残っている。そこへ古い版の起点から
fixer が走ると、**既に新形式になっている値に古い形式向けの変換が再適用される**。1.18 以降に該当する
OPTIONS fixer を実際に確認した結果:

| DataVersion | Fix | 冪等か | 影響 |
|---|---|---|---|
| 3201 | `OptionsProgrammerArtFix` | ○ 文字列置換 | 無害 |
| 3214 | `OptionsAmbientOcclusionFix` | ○ `0→false` / `1,2→true` / 他は素通し | 無害 |
| 3319 | `OptionsAccessibilityOnboardFix` | **×** 無条件に `onboardAccessibility:"false"` を set | 設定が毎回リセットされる |
| 3943 | `OptionsMenuBlurrinessFix` | **×** `float × 10` を再適用 | `5` → `50` → クランプで最大値 |
| 4651 | `OptionsGraphicsModeSplitFix` ×4 / `OptionsSetGraphicsPresetToCustomFix` | **×** 無条件 set | 26.x のグラフィック設定が毎回上書き |

**結論: 共有ファイルにファイル全体で単一の起点バージョンを当てるという前提自体が成立しない。**
各キーは「それを最後に書いたバージョンの形式」で入っているため、DataFixer をかけること自体が誤り。

**対策（実装済み）: `Options#dataFix(CompoundTag)` を HEAD で握り潰し、入力をそのまま返す。**
このメソッドは 15 バージョンすべてで `private CompoundTag dataFix(CompoundTag)` と同一シグネチャなので、
バージョン分岐なしで抑止できる。

- 各バージョンは自分の既知キーを自分の形式で読む。常に正しい。
- 未知キーに fixer をかけても、そのバージョンでは使わないので意味がない。
- 副作用: **この MOD 導入前からある古い `options.txt`**（例: 1.16 時代のもの）を初めて読むときに
  本来必要な fix が飛ぶ。
  → 対策（実装済み）: preLaunch でローカルの `options.txt` をマスターへ move した回だけは抑止しない
  （`MasterConfigService.isOptionsFreshlyMigrated()`）。以降の起動では抑止する。

`preserveUnknownOptions: false` にすると、この抑止も併せて無効化する（バニラ同等の挙動に戻す脱出ハッチ）。

もしこの方式で実害が出た場合の次善策として、
**キーごとの由来バージョンをサイドカー（`<master>/options.meta.json`）に記録し、キー単位で個別に fix する**
方法がある。正確だが実装コストが高いので、まずは上の単純案で運用して判断する。

---

## 7. `hotbar.nbt` の共通規格 — MCHF

### 問題

`hotbar.nbt` はクリエイティブのホットバー保存（9 セット × 9 スロットの ItemStack）で、
中身はバニラの ItemStack NBT そのもの。バージョン間で構造が根本的に変わる。

```
1.18 〜 1.20.4:  { id:"minecraft:diamond_sword", Count:1b,
                   tag:{ Damage:0, Enchantments:[{id:"...",lvl:5s}], display:{Name:'...'} } }

1.20.5 以降:     { id:"minecraft:diamond_sword", count:1,
                   components:{ "minecraft:damage":0, "minecraft:enchantments":{...},
                                "minecraft:custom_name":'...' } }
```

`HotbarManager` は `DataFixTypes.HOTBAR` で DataFixer をかけるが、§6 と同じく **アップグレード方向しか効かない**。
1.21 で保存したものを 1.18 に読ませることはできない。単純にファイルを共有すると、
古い版で開いた瞬間にホットバーが空になり、そのまま保存されて内容が消える。

### 解決 — 独自共通形式 (MasterConfig Hotbar Format, MCHF)

マスターには**バニラの `hotbar.nbt` を置かない**。代わりに `<master>/hotbars.dat`（gzip NBT）に
バージョン非依存の独自形式で保持し、起動時／保存時に変換する。

```
hotbars.dat (root CompoundTag)
  FormatVersion : int    = 1
  Hotbars       : List<Compound> 長さ 9（ホットバーのセット番号 0..8）
      LastSavedBy : int         # このセットを最後に書いたインスタンスの DataVersion
      Raw         : List<...>   # LastSavedBy 版のバニラ ItemStack NBT そのもの（完全再現用）
      Common      : List<Item>  # 正規化表現。長さ 9
```

**読み込み時の分岐**（`current` = このインスタンスの DataVersion）

| 条件 | 処理 | 再現度 |
|---|---|---|
| `LastSavedBy == current` | `Raw` をそのまま使う | 完全 |
| `LastSavedBy < current` | `Raw` をバニラの `DataFixTypes.HOTBAR` でアップグレード | 完全 |
| `LastSavedBy > current` | `Common` からこのバージョンの ItemStack NBT を再構築 | **不完全（変換機の担当範囲まで）** |

**保存時**は常に `LastSavedBy = current` / `Raw = 今回の内容` / `Common = 正規化した内容` の 3 点を書き換える。
最後に保存した人の意図を正としてセット単位で上書きするので、「1.21 で空にしたのに 1.18 では古いアイテムが残る」
といった矛盾は起きない。

### Common（正規化表現）のスキーマ — v1

対応表は推測ではなく、**バニラの移行 fixer から取った**。
`ItemStackComponentizationFix`（レガシー `tag` → `components`）、`CustomModelDataExpandFix`、
`UnflattenTextComponentFix` が「同じ情報を新旧でどう綴るか」の正解を持っている。

`Item` は空（= 空気）か、以下のキーを持つ CompoundTag。存在しないキーは省略する。

| キー | 型 | レガシー (`tag`) | コンポーネント |
|---|---|---|---|
| `Id` | String | `id` | `id` |
| `Count` | int | `Count` (byte) | `count` (int) |
| `Damage` | int | `tag.Damage` | `minecraft:damage` |
| `RepairCost` | int | `tag.RepairCost` | `minecraft:repair_cost` |
| `Unbreakable` | byte | `tag.Unbreakable` | `minecraft:unbreakable` = `{}` |
| `CustomModelData` | int | `tag.CustomModelData` | `minecraft:custom_model_data`（4175 以降は `{floats:[f]}`） |
| `Enchantments` | Compound | `tag.Enchantments` の `[{id,lvl}]` | `minecraft:enchantments.levels` |
| `StoredEnchantments` | Compound | 同上 | `minecraft:stored_enchantments.levels` |
| `CustomName` | String (JSON) | `tag.display.Name` | `minecraft:custom_name`（4290 以降は NBT） |
| `Lore` | List\<String\> (JSON) | `tag.display.Lore` | `minecraft:lore` |
| `DyedColor` | int | `tag.display.color` | `minecraft:dyed_color`（4307 以降は素の int） |
| `Potion` | Compound | `tag.Potion` / `CustomPotionColor` / `custom_potion_effects` | `minecraft:potion_contents` |
| `Container` | List\<Compound\> | `tag.BlockEntityTag.Items` の `{Slot,...}` | `minecraft:container` の `{slot,item}` |
| `Trim` | Compound | `tag.Trim` | `minecraft:trim`（両者とも `{material, pattern}`） |
| `Profile` | Compound | `tag.SkullOwner` | `minecraft:profile` |
| `Fireworks` | Compound | `tag.Fireworks` の `{Flight, Explosions}` | `minecraft:fireworks` の `{flight_duration, explosions}` |
| `FireworkExplosion` | Compound | `tag.Explosion` | `minecraft:firework_explosion` |
| `WrittenBook` | Compound | `tag.title` / `filtered_title` / `author` / `generation` / `resolved` / `pages` / `filtered_pages` | `minecraft:written_book_content` |
| `WritableBook` | Compound | `tag.pages` / `filtered_pages` | `minecraft:writable_book_content` |
| `Extra` | Compound | 上記に載らなかった `tag` / `components` をそのまま |
| `ExtraFrom` | int | `Extra` の出所 DataVersion |

エンチャント ID は Common では常に名前空間付きに正規化する
（実ファイルの 1.20.1 は `mending` のように名前空間なしで保存している）。

> **4307 での「アンラップ」に注意。** `TooltipDisplayComponentFix` は `show_in_tooltip` を
> `minecraft:tooltip_display` へ切り出すついでに、残りを 1 段ほどいている。
> `minecraft:enchantments` は `{levels:{id:lvl}}` → **`{id:lvl}` そのもの**に、
> `minecraft:dyed_color` は `{rgb:n}` → **素の int** になる。
> 実装当初はこれを見落としていて、1.21.5 以降にエンチャントを書き出すと壊れる状態だった。

### サブティアと `Extra` の貼り戻し

同じサブティアなら**アイテムの綴り方が完全に同じ**なので、正規化できなかった `Extra` をそのまま貼り戻せる。
サブティアが違う場合は貼り戻さない（欠落はするが、誤った形の値が混ざることはない）。

| DataVersion | 変わったこと | 由来 fixer |
|---|---|---|
| 3818 | `tag` → `components` | `ItemStackComponentizationFix` |
| 3820 | `minecraft:profile` の形 | `PlayerHeadBlockProfileFix` |
| 3945 | `minecraft:attribute_modifiers` の形 | `AttributeModifierIdFix` |
| 4055 | 同上（再度） | `AttributeIdPrefixFix` |
| 4175 | `custom_model_data` が int → `{floats:[f]}` | `CustomModelDataExpandFix` |
| 4290 | テキストコンポーネントが JSON 文字列 → NBT | `UnflattenTextComponentFix` |
| 4307 | `show_in_tooltip` の切り出しと各コンポーネントのアンラップ | `TooltipDisplayComponentFix` |

サポート対象の各バージョンは次のサブティアに落ちる。
**1.21.5 以降だけが同じティアを共有**し、それより前は 1 バージョン 1 ティアになる。

| バージョン | DataVersion | サブティア |
|---|---|---|
| 1.18.2 〜 1.20.4 | 2975 〜 3700 | 0（レガシー） |
| 1.20.6 | 3839 | 2 |
| 1.21.1 | 3955 | 3 |
| 1.21.3 | 4082 | 4 |
| 1.21.4 | 4189 | 5 |
| 1.21.5 以降 | 4325 〜 | 7 |

つまり **同世代間のダウングレードはほぼ無損失**、世代をまたぐときだけ上表の範囲に縮む。

### 正規化の根拠

対応関係はすべて**形を定義しているコードそのもの**から取っており、推測はしていない。

- レガシー ⇄ コンポーネントの対応: `ItemStackComponentizationFix`
- コンポーネント側の正確な形: 各コンポーネントのコーデック本体
  （`WrittenBookContentComponent` / `ProfileComponent` / `FireworksComponent` /
  `FireworkExplosionComponent` / `ArmorTrim` / `BannerPatternsComponent`）

上記コーデックを **1.21.2 / 1.21.4 / 1.21.8 / 1.21.11 / 26.1 の逆コンパイル済みソースで突き合わせ**、
フィールド名・構造が完全に一致することを確認した。したがってこれらは
コンポーネント時代を通じて 1 つの形として扱える。

唯一の例外が**書かれた本のページ**で、これは中身がテキストコンポーネントのため
4290 で JSON 文字列 → NBT に変わる。Common は常に JSON 文字列の形で持ち、
4290 以降のバージョンとの間だけ変換する。本のタイトルは素の文字列なので変換しない。

### v1 で正規化しないもの

**属性修正 (`minecraft:attribute_modifiers`)** と **旗の模様 (`minecraft:banner_patterns`)** の 2 つ。

- **属性修正**: `AttributeModifierIdFix`(3945) と `AttributeIdPrefixFix`(4055) で 2 回形が変わっており、
  サポート対象の中だけで 1.20.6 / 1.21.1 / 1.21.3 の 3 通りある。
  かつ 1.20.6 と 1.21.1 のソースが手元の逆コンパイル一式に無く、実際の形を確認できない。
- **旗の模様**: コンポーネント側は `[{pattern, color}]` で安定しているが、
  レガシー側は `BlockEntityTag.Patterns` の `[{Pattern:"bs", Color:15}]` という
  短縮コードと数値カラーで、変換には 40 個ほどの対応表が要る。

どちらも `Extra` に入るので**同一サブティア内では失われない**。追加するときは
1 フィールドにつき 1 か所ずつ足せる構造にしてある。

### 実装位置 — Minecraft の NBT API すら触らない

当初は「バニラの ItemStack API を触らない」だけの想定だったが、
`CompoundTag` の取得系 API 自体が 1.21.5 で `Optional` を返すよう変わるため、
**NBT の読み書きも自前で持つ**ことにした（`org.asutarisucu.masterConfig.nbt`, 約 500 行）。
NBT のバイナリ形式は 1.12 の TAG_Long_Array 追加以降変わっていないので、これで全バージョン共通になる。

結果として MCHF 層は Minecraft のクラスを一切参照せず、**素の JUnit でテストできる**。

```
@Inject(RETURN of HotbarManager#<init>)
    別スレッドで load() を先に済ませておく。get() の先頭でその完了を待つ
    （バニラは Saved Hotbars タブを初めて開いたときに読むので、量が多いとそこでゲームが止まっていた）

@Inject(HEAD of HotbarManager#load())
    <master>/hotbars.dat  →  <gamedir>/hotbar.nbt を生成（DataVersion 付き）
    その後バニラが普通に読み、必要なら自分の DataFixer で持ち上げる

@Inject(RETURN of HotbarManager#save())
    バニラが書いた hotbar.nbt を読み直し、MCHF に変換して hotbars.dat へ反映
```

**この MOD は DataFixer を一度も呼ばない。** アップグレードはバニラに任せきりで、
`Common` からの再構築はダウングレードのときだけ走る。

### 1 ファイルに DataVersion は 1 つしかない

`hotbar.nbt` はルートに `DataVersion` を 1 つ持ち、バニラはそれを起点にファイル全体へ fixer をかける。
したがって**9 グループすべてが同じバージョンの綴りになっていなければならない**。
違う版由来のグループを素通しで混ぜると、既に新形式のグループにも古い fixer が当たって壊れる。

そこで、素通しできるグループが最も多くなる版 `V` を選び（`pickFileVersion`）、
`LastSavedBy == V` のグループだけ `Raw` を素通しし、残りは `Common` から `V` の形に再構築する。
該当がなければ `V = 現行` とし、fixer は何もしない。

### 保存時に「表示しただけ」のグループを奪わない

バニラはどれか 1 つを保存しただけでも**9 グループ全部を書き直す**。
そのまま取り込むと、古い版で 1 つ保存しただけで、単に表示していた残り 8 グループの
高精度な `Raw` まで古い版のものに置き換わってしまう。

そのため取り込み時に `Extra` を除いた `Common` を比較し、**中身が変わっていないグループは触らない**。

### 対象バージョンに存在しないアイテム

`Common` からの再構築時、`Id` がそのバージョンに存在しない場合はバニラ側が読み込みで捨てる。
`Raw` は上書きされるまで残るので、新しいバージョンに戻れば元の内容が復活する。

---

## 8. リンク作成アルゴリズム

対象 1 件ごとに以下を実行する。`conflictPolicy = MASTER_WINS`（＝「共有側優先＋ローカル退避」）。

```
local  = <gamedir>/<name>
master = <masterRoot>/<name>

1. local が既に master を指すリンク            → 何もしない（正常系。毎起動ここに来る）
2. local が別の場所を指すリンク                → リンクを削除して 5 へ
3. local が実体として存在する
     3a. master が存在しない                   → local を master へ move（初回導入。データ移行）
     3b. master が存在する（衝突）              → local を masterconfig_backup/<timestamp>/ へ move
4. local が存在しない                          → そのまま 5 へ
5. master が無ければ空ディレクトリとして作成
6. リンク作成
     6a. Files.createSymbolicLink(local, master)
     6b. 失敗 & Windows & ディレクトリ         → cmd /c mklink /J <local> <master>
     6c. なお失敗                              → ERROR ログ。config/ の場合のみ §9 の縮退へ
```

### 注意点

- **Windows のジャンクションは `Files.isSymbolicLink()` で false になる。** 実測結果:

  | 対象 | `isSymbolicLink` | `attr.isOther` | `attr.isDirectory` | `toRealPath()` |
  |---|---|---|---|---|
  | ジャンクション | **false** | **true** | true | リンク先に解決される |
  | symlink | true | false | false | リンク先に解決される |
  | 実ディレクトリ | false | false | true | 自分自身 |
  | ハードリンク | false | false | false | **自分自身**（辿れない） |

  よって判定は `Files.readAttributes(p, BasicFileAttributes.class, NOFOLLOW_LINKS)` の
  `isSymbolicLink() || isOther()` を「リンクである」条件とし、リンク先の確認は `toRealPath()` で行う。
  ハードリンクは通常ファイルと区別できないため、§2 のとおり採用しない。
- 退避（3b）は **move であってコピーではない**。ディスクを二重に食わない。
  move が別ドライブ跨ぎで失敗した場合はコピー＋削除にフォールバック。
- `masterRoot` がゲームディレクトリの内側を指している場合は**無限再帰**になるため、
  起動時に検証してエラー扱いで中止する。

---

## 9. リンクできない環境での縮退

`config/` だけは Mixin なしでも救える。Fabric Loader は

```java
private Path configDir;                      // private だが final ではない
private void setGameDir(Path gameDir) { this.configDir = gameDir.resolve("config"); }
public Path getConfigDir() { if (!exists) createDirectories(configDir); return configDir; }
```

という実装で、**config ディレクトリを差し替える公式なシステムプロパティは存在しない**（調査済み）。
そこで `FabricLoaderImpl.INSTANCE` の `configDir` フィールドをリフレクションで書き換える。
非 final なので `setAccessible(true)` だけで足りる。fabric-loader は名前付きモジュールではないため
Java 17+ でもアクセス可能。

ただしこれは **`FabricLoader.getConfigDir()` を使う MOD にしか効かない**。
自前でパスを組み立てる MOD、Litematica の `schematics/`、Iris の `shaderpacks/` は救えない。
あくまで縮退動作であり、ログに明示的な警告を出す。

---

## 10. Mixin 一覧とバージョン差分

全 15 バージョンの mojmap 済み jar を `javap` で確認した結果（実測）。

| Mixin | 対象 | 目的 | バージョン差分 |
|---|---|---|---|
| `OptionsMixin` | `net.minecraft.client.Options` | `optionsFile` 差し替え ／ 未知キー保全 ／ DataFixer 抑止 | **なし** |
| `HotbarManagerMixin` | `net.minecraft.client.HotbarManager` | 起動直後に別スレッドで `load()`、`load()` 前に `hotbar.nbt` を生成、`save()` 後に MCHF へ取り込み | コンストラクタとフィールドが `File` → `Path`（1.20.2 と 1.20.4 の間） |

### `Options` — 1.18.2 から 26.2 まで完全に同一

```java
private final java.io.File optionsFile;
public Options(net.minecraft.client.Minecraft, java.io.File);   // 末尾が this.load()
public void load();
public void save();
private net.minecraft.nbt.CompoundTag dataFix(net.minecraft.nbt.CompoundTag);
```

15 バージョンすべてでこの形。**preprocessor の分岐は 1 つも要らない。**
コンストラクタのバイトコード末尾も全バージョン共通で
`... putfield optionsFile → ... → invokevirtual load() → return` なので、
`@At(value="INVOKE", target="Lnet/minecraft/client/Options;load()V")` が安定して使える。

### `HotbarManager` — パッケージに注意

`net.minecraft.client.player.inventory.HotbarManager` ではなく **`net.minecraft.client.HotbarManager`**。
`Hotbar` の方が `net.minecraft.client.player.inventory.Hotbar` にある。

```java
private final java.io.File optionsFile;      // 1.18.2 〜 1.20.2
private final java.nio.file.Path optionsFile;  // 1.20.4 〜 26.2
public HotbarManager(java.io.File / java.nio.file.Path, com.mojang.datafixers.DataFixer);
private void load();
public void save();
```

分岐点は **1.20.4**（= `MC >= 12004`）。ただし MCHF はパス差し替えをせず
`load()` / `save()` の前後に挟むだけなので、この差分には触れずに済む見込み。

`resourcepacks/` / `shaderpacks/` / `schematics/` / `config/` はリンクで解決するため **Mixin 不要**。
MCHF の変換機も NBT ↔ NBT で完結するため Mixin 不要。
結果として、バージョン差分を持つコードは **Mixin 2 クラス＋変換機 1 クラス**に閉じる。

---

## 11. 多バージョン対応（テンプレート適用）

`settings.json` を 1.18 以降に絞る:

```
1.18.2, 1.19.4, 1.20.1, 1.20.2, 1.20.4, 1.20.6,
1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11,
26.1.2, 26.2
```

計 15 サブプロジェクト。`build.gradle` の `preprocess` ノードから 1.14.4 / 1.15.2 / 1.16.5 / 1.17.1 を削除し、
`versions/mainProject` を `1.21.1`（開発の主軸）に変更、`versions/1.1[4-7].*/` ディレクトリを削除する。

Java ターゲット（`common.gradle` が自動判定）:

- 1.18.x 〜 1.20.4 → Java 17
- 1.20.6 〜 1.21.11 → Java 21
- 26.x → Java 25（かつ難読化なし = `fabric-loom` を素で使う分岐）

ビルド: `./gradlew buildAndGather` で全バージョンの jar が `build/libs/` に集まる。
開発機に JDK 25 が入っていることは確認済み。

Mod メタデータ:

- `mod_id` = `masterconfig`
- `mod_name` = `MasterConfig`
- `maven_group` = `org.asutarisucu`
- パッケージ = `org.asutarisucu.masterConfig`
- リポジトリ = 後日作成（`fabric.mod.json` の `contact.sources` は決まり次第埋める）

> 注: Java のパッケージ名は慣習上すべて小文字だが、指定どおり `masterConfig` とする。
> 文法上は問題なく、大文字小文字を区別しないファイルシステム上でも `mod_id`（`masterconfig`）とは
> ディレクトリ階層が別なので衝突しない。

---

## 12. 安全策

| 項目 | 対策 |
|---|---|
| 多重起動 | `<master>/.masterconfig_lock` に PID とゲームディレクトリを 1 行ずつ書く。生きている他インスタンスがいれば WARN ログ（起動は止めない）。終了時に自分の行を消し、クラッシュで残った行は PID が生きているかで判定して捨てる |
| マスタールートが未設定 | 何もせず WARN。ゲームは通常どおり起動する |
| マスタールートがゲームディレクトリ内 | ERROR で中止（無限再帰の防止） |
| 移行時のデータ消失 | 削除は一切行わない。退避は move のみ。`masterconfig_backup/<timestamp>/` に残す |
| MOD 自体が原因の起動失敗 | `masterconfig.json` の `enabled: false`、または `-Dmasterconfig.disable=true` で完全に無効化 |
| リンク作成失敗 | その対象だけスキップして続行。ゲームは起動する |

---

## 13. 残存リスク（設計レビュー対象）

1. **MCHF のダウングレード経路は無損失にならない**
   `LastSavedBy > current`（新しい版で保存したものを古い版で開く）のときだけ `Common` 経由になり、
   §7 の表に載っていない属性は落ちる。落ちた内容は WARN に出す。
   `Raw` は上書きされるまで保持されるので、新しい版に戻れば元の内容が復活する。
   → 正規化の対象範囲は実運用で不足が判明したら追加する。初版は表の範囲で確定させる。

2. **`options.txt` の DataFixer 抑止の副作用**
   §6 のとおり fixer を抑止すると、MOD 導入前からある古い `options.txt` に本来必要な fix が飛ぶ。
   初回 move のときだけ素通しする方針だが、「1.16 の options.txt を持つインスタンスに 1.18 で初回導入し、
   その後 1.21 で開く」ようなケースでは 1.18→1.21 の fix が飛ぶ。
   実害は `ao` のような一部キーがデフォルトに戻る程度と見積もっているが、要確認。

3. **MOD config のバージョン跨ぎ**
   `options.txt` と同じ「未知キーが消える」問題は各 MOD の config にも起こりうるが、
   フォーマットが MOD ごとに違うため汎用の対策はない。実害が出た MOD が判明したら
   その対象だけマスターから外す（`extraLinks` で個別にバージョン別パスへ逃がす）運用で対応する。

4. **26.x 系の互換**
   難読化なし・大規模変更のため `Options` / `HotbarManager` の構造が変わっている可能性がある。
   実装時に最優先で確認する。最悪 26.x のみ対象外にできるよう、バージョン分岐は独立させておく。

---

## 14. 実装状況

| # | 内容 | 状態 | 検証 |
|---|---|---|---|
| 1 | テンプレート導入・1.18 以降へ削り込み・リネーム | 完了 | `buildAndGather` が 15 バージョン成功、jar 15 個 |
| 2 | `masterconfig.json` ＋ preLaunch の骨組み | 完了 | 設定ファイル自動生成、マスタールートをログ出力 |
| 3 | リンク作成エンジン（§8 の状態遷移） | 完了 | 単体テスト 9 件で全分岐。実ゲームで 4 ディレクトリがリンク化、既存 config は移行 |
| 4 | OptionsMixin — パス差し替え | 完了 | `<master>/options.txt` に書かれ、ゲームディレクトリには生成されない |
| 5 | OptionsMixin — 未知キー保全 ＋ DataFixer 抑止 | 完了 | 1.21.1 → 1.18.2 → 1.21.1 の実機往復でキー喪失 0、`menuBackgroundBlurriness` も不変 |
| 6 | MCHF 変換機（Common ⇄ ItemStack NBT） | 完了 | 単体テスト 27 件。実プレイヤーの `hotbar.nbt` 15 個（最大 87MB）でコーデックがバイト一致往復 |
| 7 | HotbarManagerMixin | **一部** | 読み込み方向は実機確認済み。**保存方向は単体テストのみ**（下記） |
| 8 | extraLinks（任意フォルダ） | 完了 | ローカル移行・共有リンク・パス脱出の拒否を実機確認 |
| 9 | 多重起動の検出（§12） | 完了 | 1.21.1 と 1.18.2 の同時起動で警告、強制終了後の残骸行も次回起動で除去 |
| 10 | 全 15 バージョンで実起動確認 | 未 | 1.18.2 と 1.21.1 のみ実起動済み |

### 手動で確認が必要な残件

自動操作ではゲーム（GLFW）に **Ctrl + 数字**を届けられなかったため、以下は手動確認が要る。
`keyboard_shortcut`、スキャンコード付き `keybd_event`、Ctrl を別プロセスから押しっぱなし、いずれも不発だった。

1. **ホットバーの保存方向**
   クリエイティブでインベントリを開き、Saved Hotbars タブで **Ctrl+1**。
   ログに `Saved N hotbar group(s) to the master root` が出れば正常。
   その後もう一方のバージョンで起動し、同じホットバーが並ぶことを確認する。
   - 読み込み方向は実機確認済み: 1.20.1 で保存された実ホットバー 5 セットを `hotbars.dat` に
     入れておくと、1.21.1 のクリエイティブ画面に全アイテムがそのまま並ぶ
     （ログ: `Loaded the shared hotbars, written for DataVersion 3337`）。
     レガシー `tag` → コンポーネントの変換はバニラの DataFixer が行っている。
   - 保存側のロジック（`HotbarSharing.afterSave` / `HotbarStore.absorbNativeHotbarFile`）は単体テスト済み。
     未確認なのは `@Inject(RETURN of save)` が実際に発火するところだけで、
     機構は検証済みの `load` 側と同一。

2. **1.20.6 / 1.21.3 / 1.21.4 / 1.21.5 以降でのアイテム往復**
   `minecraft:enchantments` のアンラップ（4307）や `custom_model_data` の展開（4175）は
   fixer のソースから導いたもので、実データでの確認はまだ。
   各バージョンでエンチャント付き・名前付きのアイテムをホットバーに保存し、
   別バージョンで開いて内容が保たれることを見るのが確実。

3. **残り 13 バージョンの実起動**
   `./gradlew :<version>:runClient` で起動し、ログにスタックトレースが出ないことと
   `config/` などがリンク化されることを確認する。

## 15. 再精査で見つけて直した不具合

実装後にコード全体を読み直して見つけたもの。すべて回帰テスト付きで修正済み。

### 情報を持ち過ぎたコンポーネントを丸ごと食べていた（3 件）

`Common` に写しきれないフィールドを持つコンポーネントを `Extra` から取り除いてしまい、
「同じサブティア内では失われない」という約束を破っていた。

| コンポーネント | 落としていたもの |
|---|---|
| `minecraft:unbreakable` | 4307 未満での `show_in_tooltip` |
| `minecraft:dyed_color` | 同上（4307 未満は `{rgb, show_in_tooltip}`） |
| `minecraft:custom_model_data` | 4175 以降の `flags` / `strings` / `colors` |

→ **`Common` が全部を表現できないコンポーネントは `Extra` に残す**。
書き戻しは `Extra` が供給しなかったときだけ `Common` から組み立てる、という規則に統一した。

### `minecraft:potion_contents` の取りこぼし（1 件）

コーデックが `Codec.withAlternative` で**ポーション ID の文字列そのもの**も受け付けるが、
コンパウンドしか見ていなかったため、その形だと正規化されず素通りしていた。
またレコードには `custom_name` という 4 番目のフィールドがあり、
コンポーネントごと取り除いていたのでこれも落ちていた。

### NBT リストの型が混ざって例外になる（2 件）

NBT のリストは 1 種類の型しか持てない。

- **Lore**: 4290 以降へ書き戻すとき、`{"text":...}` は Compound に、
  JSON でない行は String になる。両方が混ざるとリスト構築で
  `IllegalArgumentException` が飛び、ホットバー変換全体が中断していた。
  → 混在したら全部を素の文字列形式にフォールバックする（テキストコンポーネントとして正当）。
- **本のページ**: コーデックは `{raw: ページ}` の省略形としてページ単体も受け付ける。
  4290 以降はその省略形も Compound なので、`raw` キーの有無でしか区別できない。
  区別できていなかったため、省略形のページが変換されずに素通りしていた。
  → 省略形は常に `{raw: ...}` へ展開する。

### 保存のたびにバックアップが増えていた（1 件）

`hotbar.nbt` にはこの MOD が生成したことを示す印を付けているが、
ゲームが保存すると印のないファイルで上書きされる。そのため次回起動時に
「MOD 導入前からあるファイル」と誤判定し、**保存後は毎回バックアップへ退避**していた。
データは失われないがゴミが溜まり続ける。
→ 取り込み後に印を付け直す。

### `move` が既存の宛先を黙って上書きしていた（1 件）

`PathLinks.move` はボリュームを跨ぐ移動のために「コピーしてから元を削除」へフォールバックする。
ところが宛先が既にある場合の `FileAlreadyExistsException` もそこへ落ちていたため、
**宛先を上書きしたうえで元を削除**していた。
バックアップ同士が衝突すると先のバックアップが消える。
→ 宛先が存在する時点で例外にする（「削除は一切しない」という §12 の方針に合わせる）。

### `extraLinks` にルートを指定できてしまう（1 件）

`{"game": "", "master": ""}` や `"."` はゲームディレクトリそのものに解決され、
状態遷移の 3b（マスター側優先で退避）に入ると**インスタンス全体を自身のバックアップ配下へ移動**しようとする。
→ どちらかの端がルート、あるいはこの MOD 自身のバックアップ先や設定ファイルなら拒否する。

### 直していない既知の制限

- **テキストコンポーネントの byte と int**: 4290 以降の NBT テキストで `{bold:1b}` は
  JSON を経由すると `{bold:1}`（int）に戻る。Minecraft のコーデックは数値を真偽値として読むので
  意味は変わらないが、バイト単位では一致しない。これは
  `UnflattenTextComponentFix` が JSON → NBT の一方向変換しか定義していないことに由来する。
- **`extraLinks` の入れ子パス**: `sub/config` のように末尾の名前が衝突する 2 つを同時に退避すると、
  バックアップ先が競合してその対象だけ失敗する（黙って壊れることはない）。

---

## 16. 未確定事項

- GitHub リポジトリ名（後日作成。決まり次第 `fabric.mod.json` と README に反映）
- ライセンス（テンプレートは LGPL-3.0。踏襲するか）
- 配布（Modrinth / CurseForge / jitpack）— 後日指示待ち。それまで publish タスクは無効のまま
- MCHF `Common` の正規化対象（§7 の表）にこれ以上追加するか
