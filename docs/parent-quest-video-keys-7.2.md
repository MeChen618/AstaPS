# Parent-quest video data on 7.2 (#223)

This patch implements the missing video-key request and restores the two video strings in
`ParentQuest`. It needs matching local video metadata. It does **not** establish that every
reported black screen is fixed: playback still needs an in-game check with the affected assets.

## Verified protocol

The reference is the official CN Android ARM64 7.2.0 client
(`7.2.0_48751704_49008628`), not the 7.1 packet numbers in the issue comment.

| Message | CmdID | Field | Meaning established by the client |
| --- | ---: | ---: | --- |
| `GetParentQuestVideoKeyReq` (`LHOHHGOFGOL`) | 2974 | 2 | Parent quest ID, uint32 |
| `GetParentQuestVideoKeyRsp` (`CKMJCGBJBPE`) | 9768 | 3 | `MGKAKJNCIEI`, second cached string (`key2`) |
| same response | 9768 | 4 | `IGLPPIEKGMH`, first cached string (`key1`) |
| same response | 9768 | 5 | Parent quest ID, uint32 |
| same response | 9768 | 14 | Retcode, int32 |
| `ParentQuest` (`KFFKELOGEDE`) | nested | 395 | `IGLPPIEKGMH`, `key1` |
| same parent | nested | 773 | `MGKAKJNCIEI`, `key2` |

The names `video_key_1` and `video_key_2` describe cache order, **not** a proven assignment
to AES/audio/legacy key algorithms. They are opaque strings. Do not cast them to a Java long,
reuse `QuestEncryptionKeys.json`, put a uint64 at response field 14, or copy 7.1 CmdID 3200.

Reproducible static evidence, without executing the client:

- Official APK SHA-256:
  `7609d801d53fef2b4bdf27414ed3d7a4092df167012cb34bd79cebff6d150cff`.
  ELF build ID: `6d85e3141bcd6b508f3e853bab508cc407278eac`.
- `0x95abdd8` checks the parent video cache. A miss calls `0xb2b5368` at `0x95abe18`,
  then waits for response `0x2628` (9768) at `0x95abe60`.
- `0xb2b53a4` loads request type-info slot `0x1589b0a8`, allocates it, stores the
  parent ID at object offset `0x18`, then sends it. That type's CmdID getter at
  `0x9cd8608` returns `0xb9e` (2974); its writer at `0x9cd8610` emits tag `0x10`.
- The response parser at `0x121d0008` reads wire fields 3/4 into offsets `0x20`/`0x18`,
  parent ID into `0x28`, and retcode into `0x2c`. Its CmdID getter returns 9768.
- The response callback at `0xb2c5754` checks retcode and passes offsets `0x18`/`0x20`
  to cache writer `0x8231b60`. Parent updates call that same writer at `0xad387f8`.
- The matching 7.2 schema is also available in
  [go_proto/7.2.0](https://github.com/MeChen618/go_proto/tree/main/7.2.0).

## Local resource file

Create `ParentQuestVideoKeys.json` in the configured resources directory, or in the
configured user data directory (normally `resources/` and `data/`). User data records
override resource records by parent quest ID. Restart the server after changing it.

The format is a JSON array:

```json
[
  {
    "parentQuestId": 5006,
    "key1": "<exact IGLPPIEKGMH string for this client and video asset>",
    "key2": "<exact MGKAKJNCIEI string for this client and video asset>"
  }
]
```

The angle-bracket text is a format illustration, **not playable data**. Replace it with
verified values before use. No production keys are bundled in this patch.

At least one nonblank component is required; an unused component may be omitted or empty.
Both components must be JSON **strings**, even if a value consists entirely of decimal
digits. IDs must be positive signed 32-bit integers or their decimal string representation.
Conflicting duplicate IDs and malformed records reject that entire file, so no partial
table is installed. An invalid data override leaves the successfully loaded resource table
in place. Startup reports the loaded record count and file-level errors without key contents.

The public table mentioned in
[#223](https://github.com/MeChen618/AstaPS/issues/223#issuecomment-7109508380)
uses names such as `videoKey`, `audioKey`, and `aesKey`. The issue comment demonstrates
a 7.1 legacy key, but does not prove the assignment of both 7.2 string fields. This patch
therefore does not automatically import that table or infer IDs from version/video names.
Use a mapping that has actually been verified for the deployed client and assets.

The server sends both components in login parent records, ordinary updates, command-added
parent updates, and on-demand requests. An on-demand request can refer to an archived parent
absent from the active quest manager; this supports Travel Log without accepting/finishing
that quest. A missing record receives nonzero `RET_FAIL`, with no invented key strings.

## Local Codex / client verification

1. Build this branch with JDK 21. Run
   `./gradlew test jar -PexcludeTags=integration -PskipHandbook=1`.
2. Install the new server jar and the verified local video table. Check the startup record
   count. Keep the client version, video assets and metadata from the same tested set.
3. Use the normal story path and Travel Log replay for the affected parent quest. For normal
   progression, include the issue's scenes 20363/20366/20266 and world scene 3 as applicable.
   Also test reported parent IDs 5006, 3028 and 6018 where the matching quest resources exist.
4. Confirm the ordinary parent notifications carry string fields 395/773. After a fresh client
   session, a cache miss must send 2974 and receive 9768, with parent ID field 5, string fields
   3/4 and a zero retcode. Retcode 14 must never contain a video key. Keys already in the
   cache can legitimately mean no new request is sent.
5. Confirm a real video frame and audio play, completion resumes the intended story, and
   replay does not alter saved quest progress. Test one missing-table parent as well: the
   response should fail, rather than report an empty successful key.
6. If playback remains black despite the correct request, response and verified table,
   collect the client video error, parent ID, cutscene/video name, client build and asset
   version. Redact key values in any public log. A codec/missing-asset error needs its own fix.

Automated coverage uses synthetic values: exact request/response wire fields, cache order,
large decimal-string precision, absent metadata, conflicting/invalid records, login and all
parent update variants, and preservation of quest state. No actual client playback has been
performed in this environment; keep #223 open until the affected videos pass this check.
