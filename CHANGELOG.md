## [1.15.0](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.14.4...v1.15.0) (2026-10-15)

### 🐛 Bug Fixes

* **Google Photos - Build:** update fast-build.sh patch -e flags to use consolidated patch names ([0774973](https://github.com/Akash-Sriram/morphe-google-photos/commit/0774973fb4b43c1e3bdd9b554bc0ea1043b45cff))
* **Google Photos - Deps:** bump handlebars to 4.7.10 to resolve Dependabot alerts [#83](https://github.com/Akash-Sriram/morphe-google-photos/issues/83) and [#84](https://github.com/Akash-Sriram/morphe-google-photos/issues/84) ([79eb97d](https://github.com/Akash-Sriram/morphe-google-photos/commit/79eb97d2f8825633674b536b5ccc6cf34366bc75))
* **Google Photos - Flags:** adjust floating pill bottom margin dynamically with navigation bar insets ([c11bb7c](https://github.com/Akash-Sriram/morphe-google-photos/commit/c11bb7c4ad11c3a1bc7b561c72369b9f227fdaa9))
* **Google Photos - Gms:** rename all package permissions and component permissions on package change ([cc4ba67](https://github.com/Akash-Sriram/morphe-google-photos/commit/cc4ba676334e4b8d6ce4707bcfdfaa7a95fca51f))
* **Google Photos - Toolkit:** archive all remote_media rows and match remote_media_key ([2f164ef](https://github.com/Akash-Sriram/morphe-google-photos/commit/2f164ef72f26e6e52bdd4fae330635963473576d))
* **Google Photos - Toolkit:** authenticate thumbnail fetching via Glide so similarity hashing works on cloud photos ([69d0ca6](https://github.com/Akash-Sriram/morphe-google-photos/commit/69d0ca6cfe3a907184128fb1ccff986f7c960405))
* **Google Photos - Toolkit:** convert HARDWARE bitmaps to software ARGB_8888 in calculateAverageHash ([a637b7c](https://github.com/Akash-Sriram/morphe-google-photos/commit/a637b7c37c0623b334ec16bb85ff80e39c4dfcac))
* **Google Photos - Toolkit:** correct Glide load method invocation in fetchThumbSync + add similarity diagnostics ([86203ed](https://github.com/Akash-Sriram/morphe-google-photos/commit/86203ed700c1ee0647f9d0d2fc0c6f3a032b6f6b))
* **Google Photos - Toolkit:** correct obfuscated class names for ajae and ajhx in locked folder unlock ([8cbbcd6](https://github.com/Akash-Sriram/morphe-google-photos/commit/8cbbcd63c3df5a097575bc34aebcd99eb50207e2))
* **Google Photos - Toolkit:** correct SQL column alias in syncPendingLocalTrash subquery ([9e3c033](https://github.com/Akash-Sriram/morphe-google-photos/commit/9e3c03325744799c83cefc6346e4e0791812bd0b))
* **Google Photos - Toolkit:** create personal album via AddMediaToAlbumTask and fix title sync ([5152fc4](https://github.com/Akash-Sriram/morphe-google-photos/commit/5152fc4b70d338050c0c708126a271dab9ccdfdf))
* **Google Photos - Toolkit:** dedupe remote_media rows by dedup_key/canonical and stop writing local_media trash_timestamp ([3c3aba3](https://github.com/Akash-Sriram/morphe-google-photos/commit/3c3aba3f21f27276cff7891780ddf2995404719f))
* **Google Photos - Toolkit:** dispatch bjir ActionWrapper online trash RPC directly for cloud trashing ([80b1a21](https://github.com/Akash-Sriram/morphe-google-photos/commit/80b1a21745a7e1b0a1d9d16286f2e5eb85b451f8))
* **Google Photos - Toolkit:** dynamically update filter count per source and align locked folder filters/actions with web GPTK ([cdb976e](https://github.com/Akash-Sriram/morphe-google-photos/commit/cdb976e166a42b402ccea2ce241f5dc3dcaa5070))
* **Google Photos - Toolkit:** enhance RadioGroup touch spacing, explicit tag mapping, and plural Type filter labels ([871f6aa](https://github.com/Akash-Sriram/morphe-google-photos/commit/871f6aa22a714c1faa237d841e9edb55b7f92710))
* **Google Photos - Toolkit:** execute bjir.a, bjir.d direct RPC and aokq queue dispatch for online trashing ([7d7aaaf](https://github.com/Akash-Sriram/morphe-google-photos/commit/7d7aaaf9a83e193fe743dab0efbf7ebb2b19e721))
* **Google Photos - Toolkit:** execute CreateEnvelopeTask synchronously via bxue.f and use valid creation type 4 ([2f72721](https://github.com/Akash-Sriram/morphe-google-photos/commit/2f727219e54db4d91e80f465c4f6c26968c77e55))
* **Google Photos - Toolkit:** execute DeleteSharedCollectionTask and MoveToTrashActionWrapper synchronously without premature SQLite deletes ([269488f](https://github.com/Akash-Sriram/morphe-google-photos/commit/269488f1e2d40d801e4a266e2c85da285434c92e))
* **Google Photos - Toolkit:** execute RemoveCollectionTask synchronously, clean up SQLite album rows, and restore akjz.b for trash ([3d0f64a](https://github.com/Akash-Sriram/morphe-google-photos/commit/3d0f64a0e9904510a268fe51348fc800988fdb8b))
* **Google Photos - Toolkit:** execute synchronous online mars remove task and sync sqlite locked tables ([9a1e4b5](https://github.com/Akash-Sriram/morphe-google-photos/commit/9a1e4b50a4ce760cb2a40e71aad1d52cfd9f5061))
* **Google Photos - Toolkit:** implement chunked archive & unarchive with direct RPC and UI notifications ([44c14ac](https://github.com/Akash-Sriram/morphe-google-photos/commit/44c14ace186c9225123ac0ac1a6dfde9bf5d2ebe))
* **Google Photos - Toolkit:** inject onMarsGridActivityCreated at index 0 to avoid clobbered p0 register ([f88ec26](https://github.com/Akash-Sriram/morphe-google-photos/commit/f88ec263dd173fb79bf56aab35f3f5cfedfd04be))
* **Google Photos - Toolkit:** install SQLite archive sync triggers and prioritize library canonical keys ([d25edef](https://github.com/Akash-Sriram/morphe-google-photos/commit/d25edefaf4b4617db61e7c293db38e92a6d2669e))
* **Google Photos - Toolkit:** precisely check runtime backup toggles for locked folder ([097ca2e](https://github.com/Akash-Sriram/morphe-google-photos/commit/097ca2e0f40df1ab087307259571269ff544b08c))
* **Google Photos - Toolkit:** preserve MarsMoveTask class name from bytecode renamer ([1d7931a](https://github.com/Akash-Sriram/morphe-google-photos/commit/1d7931a3eb0bf05896f69f12bae3966e021c279c))
* **Google Photos - Toolkit:** prevent album rows from leaking as unarchived and synchronize archive state in heal query ([2d448ca](https://github.com/Akash-Sriram/morphe-google-photos/commit/2d448ca78172645598ade4984b4f4d00f12cc723))
* **Google Photos - Toolkit:** prevent avatar hijacking on chevrons, remove duplicate discovery chips, and fix MarsRemoveTask reflection ([1eb4f17](https://github.com/Akash-Sriram/morphe-google-photos/commit/1eb4f17e2ed355cda47890bc893c1dffc250a0aa))
* **Google Photos - Toolkit:** prevent avatar patch from hijacking dialog close button ([60b9934](https://github.com/Akash-Sriram/morphe-google-photos/commit/60b9934ac745cdf86a29dba2133b1d150c57ef3a))
* **Google Photos - Toolkit:** prevent background scan race crash and enforce locker backup notice ([e278deb](https://github.com/Akash-Sriram/morphe-google-photos/commit/e278debaf397a04792d77f346638d7c87c4f24bd))
* **Google Photos - Toolkit:** prevent non-canonical album copies from retaining ghost archive flags and reconcile media table ([b1fb1e3](https://github.com/Akash-Sriram/morphe-google-photos/commit/b1fb1e338ea1154589042f6ebfe56ad8dd36b013))
* **Google Photos - Toolkit:** prevent section 4 control pills from overflowing offscreen ([c108977](https://github.com/Akash-Sriram/morphe-google-photos/commit/c1089774874e718b79f5726924e9d58072ce70bf))
* **Google Photos - Toolkit:** prioritize RequestManager.load and enforce isModelSet for Glide preview rendering ([fd22f78](https://github.com/Akash-Sriram/morphe-google-photos/commit/fd22f7873dcf236a1319801912eab22e6d30f59e))
* **Google Photos - Toolkit:** query valid columns for media table in archive and unarchive ([e06de27](https://github.com/Akash-Sriram/morphe-google-photos/commit/e06de278190a8f51e70a8861f3de2d1790d7465d))
* **Google Photos - Toolkit:** reduce move/unlock batch chunk size to 5 for lively progress & fix filter header chevron layout ([caa000a](https://github.com/Akash-Sriram/morphe-google-photos/commit/caa000acb1c8ec7fc65521dd12f300d2c45dfb3f))
* **Google Photos - Toolkit:** replace asymmetric triggers with symmetric single-source-of-truth archive triggers ([2aa0daa](https://github.com/Akash-Sriram/morphe-google-photos/commit/2aa0daa30c2f0c2fa8efba8bdcdef3b86e02db17))
* **Google Photos - Toolkit:** reset last_archive_origin on unarchive, match dedup/media keys, and notify native media monitors ([d7a1def](https://github.com/Akash-Sriram/morphe-google-photos/commit/d7a1defbc989fe2508f5bc196f62318b42ceae76))
* **Google Photos - Toolkit:** resolve AddMediaToAlbumTask builder class dynamically to fix New Album creation ([b312b80](https://github.com/Akash-Sriram/morphe-google-photos/commit/b312b802e8d30ad2359e49ab408a9f1e7da916da))
* **Google Photos - Toolkit:** resolve album loading query and auto-clean filter state on open/refresh ([17ce6f7](https://github.com/Akash-Sriram/morphe-google-photos/commit/17ce6f7561ce7d5fe90c77090730436ab937fa45))
* **Google Photos - Toolkit:** resolve album title omission and speed up item addition ([cdc9b4b](https://github.com/Akash-Sriram/morphe-google-photos/commit/cdc9b4b790f7f141bf93458c4e05530a7c731ca3))
* **Google Photos - Toolkit:** resolve archived filter via media table for shared_media (Albums source) ([c271bc8](https://github.com/Akash-Sriram/morphe-google-photos/commit/c271bc8b7b87eae7308d5d24dbadcccdfb60ddb9))
* **Google Photos - Toolkit:** resolve canonical cloud key and ensure optimistic trash updates for album items ([71defa9](https://github.com/Akash-Sriram/morphe-google-photos/commit/71defa987bbca5f303a7296245f0fb2f2e7a2db2))
* **Google Photos - Toolkit:** resolve dedupKey to library remote_media_key for cloud trash and invoke RemoveCollectionTask for album deletion ([40726a7](https://github.com/Akash-Sriram/morphe-google-photos/commit/40726a7476eff8120284664b3a8d7eb354b851c9))
* **Google Photos - Toolkit:** resolve Glide RequestBuilder into-ImageView reflection and multi-build Locked Folder unlocking ([3074475](https://github.com/Akash-Sriram/morphe-google-photos/commit/30744750c176811f0efd896872c1b4835bd9ec0f))
* **Google Photos - Toolkit:** resolve Glide thumbnail preview and multi-build Trash restore ([45dd3c2](https://github.com/Akash-Sriram/morphe-google-photos/commit/45dd3c27c9498e8cafcf792268f9911440d814aa))
* **Google Photos - Toolkit:** resolve keys for trash/restore, chunk online tasks, and fix MoveToTrashActionWrapper ([f73e346](https://github.com/Akash-Sriram/morphe-google-photos/commit/f73e346c9af2bab4cfe795d843405da3d82fa2a4))
* **Google Photos - Toolkit:** resolve remove-from-album task and add locked folder backup status dialogs ([e3b1cfd](https://github.com/Akash-Sriram/morphe-google-photos/commit/e3b1cfdc2801df8b6c2543b4bd17b7d75e713763))
* **Google Photos - Toolkit:** resolve trash items via remote_media state 2 soft-deleted flag ([5b4a6dd](https://github.com/Akash-Sriram/morphe-google-photos/commit/5b4a6dd42c10bb098d002dbb3a976251d40db9cd))
* **Google Photos - Toolkit:** safeguard Locked Folder activity resolution from bytecode renamer ([12ea025](https://github.com/Akash-Sriram/morphe-google-photos/commit/12ea0258b603164f85b90aef79de7a281f8302ec))
* **Google Photos - Toolkit:** set archive source enum to USER_ACTION (2) to resolve INVALID_ARGUMENT from Google backend ([d3a91cb](https://github.com/Akash-Sriram/morphe-google-photos/commit/d3a91cbb9d1d7214e90bc4047c8c6b6c49f90a10))
* **Google Photos - Toolkit:** shield MarsMoveTask reflection from GmsCoreSupport bytecode renamer ([0fc1328](https://github.com/Akash-Sriram/morphe-google-photos/commit/0fc1328a6fa7f284939103a9515dbe61f9718920))
* **Google Photos - Toolkit:** support 7.97 album remove task reflection and multi-source locked folder backup detection ([a9a6b3c](https://github.com/Akash-Sriram/morphe-google-photos/commit/a9a6b3ce9b385af11c77a3fb1563064abc077e1d))
* **Google Photos - Toolkit:** support Google Photos type=2 for video filter ([397aba5](https://github.com/Akash-Sriram/morphe-google-photos/commit/397aba5aea2a3bd004099f9e0f716e29c2afadda))
* **Google Photos - Toolkit:** support unarchive via psu.a, attach ArchiveFeature, fix SQLite lock ordering, and heal conflicting trash flags ([3f13899](https://github.com/Akash-Sriram/morphe-google-photos/commit/3f138993468dd881fc24966ba8ccd9823618ae9d))
* **Google Photos - Toolkit:** sync album toTrash with Google Photos web backend and fix album queries ([2e92b4a](https://github.com/Akash-Sriram/morphe-google-photos/commit/2e92b4af36156b3c582d61f617926d3c8f91bbf1))
* **Google Photos - Toolkit:** sync cloud album creation with title and resilient AddMediaToEnvelopeTask ([3669f3d](https://github.com/Akash-Sriram/morphe-google-photos/commit/3669f3d5773d50a89312db3e0f3f0f13737a2b66))
* **Google Photos - Toolkit:** sync cloud trash and execute official online restore task ([dfd4587](https://github.com/Akash-Sriram/morphe-google-photos/commit/dfd4587c4c50f8cbfb151a34e259e0111db9765b))
* **Google Photos - Toolkit:** upgrade aHash to dHash for filter-robust similarity + fix clustering + remove broken avatar patch ([75b5e5f](https://github.com/Akash-Sriram/morphe-google-photos/commit/75b5e5fc5342b8b677caf31ca1ad44af4400b7e3))

### ✨ New Features

* **Google Photos - Build:** unlock full Morphe CLI capabilities (JVM tuning, ABI stripping, bytecode mode, mount install, resilient adb) ([26e094b](https://github.com/Akash-Sriram/morphe-google-photos/commit/26e094b80293a6d0f30912c4c07db66e3276e619))
* **Google Photos - Cli:** add update-morphe-cli.sh script and integrate with fast-build ([02f0de2](https://github.com/Akash-Sriram/morphe-google-photos/commit/02f0de2567f38d94b301189e00f505701c2ee447))
* **Google Photos - Diagnostics:** add file export, GitHub markdown report, crash recovery dialog, and issue log formatting ([85197e9](https://github.com/Akash-Sriram/morphe-google-photos/commit/85197e9af6911f6750ebb9b0299af2e47d590b12))
* **Google Photos - Diagnostics:** optimize log manager with noise suppression, mutual filtering and toolkit isolation ([547bd77](https://github.com/Akash-Sriram/morphe-google-photos/commit/547bd778fe146bbd19cc8a7cf966d7e4b142cc89))
* **Google Photos - Diagnostics:** replace feature-specific chips with universal severity and scope filters ([d1853f0](https://github.com/Akash-Sriram/morphe-google-photos/commit/d1853f023ed793898632e86b408d4fc00c828d62))
* **Google Photos - Diagnostics:** respect active category and query filters in copy, export, and share ([3ee9c8c](https://github.com/Akash-Sriram/morphe-google-photos/commit/3ee9c8c8ee786ce0b278c08996fe6c91b2d05609))
* **Google Photos - Flags:** add components option to select Flag manager, Toolkit, or both ([19f0a62](https://github.com/Akash-Sriram/morphe-google-photos/commit/19f0a622192b40c5d76c834cb43bb86a74918aec))
* **Google Photos - Flags:** remove 5 dead legacy flags purged by Google from registry ([738c08b](https://github.com/Akash-Sriram/morphe-google-photos/commit/738c08b23073b58cc4ab8450cf4bdac218399f7b))
* **Google Photos - Flags:** remove redundant baseline-matching flag 45838904 from registry ([da590de](https://github.com/Akash-Sriram/morphe-google-photos/commit/da590def76f11f13ae0b0e03663fd29368c99edd))
* **Google Photos - Flags:** remove reels video feed flags from registry ([1efb11a](https://github.com/Akash-Sriram/morphe-google-photos/commit/1efb11ab52df074dd4dc268dc488c0c2608c0019))
* **Google Photos - Flags:** set default components option to Flag manager only ([ca6d30f](https://github.com/Akash-Sriram/morphe-google-photos/commit/ca6d30f7b2e916a8fdb4650ea5583c44453e456e))
* **Google Photos - Flags:** toggle shelf layout flags in baseline and add 45838904 to flag manager ([2d44fe8](https://github.com/Akash-Sriram/morphe-google-photos/commit/2d44fe84d10c0c50d6a849d52ea6ca386be5c5de))
* **Google Photos:** hook AI model manager easter egg on version long-press & authenticated DCIM export ([1beb038](https://github.com/Akash-Sriram/morphe-google-photos/commit/1beb0385836d2ae9210a85103feb6196f307e71f))
* **Google Photos - Phenotype:** sync baseline phenotype.xml to official 7.97.0 and remove defaulted flag 45839387 ([59b6832](https://github.com/Akash-Sriram/morphe-google-photos/commit/59b6832fdbd2e8ea89609687cb385e6d67081510))
* **Google Photos:** add Google Photos 7.97.0.997578476 compatibility and universal multi-build Glide resolver ([ad8039a](https://github.com/Akash-Sriram/morphe-google-photos/commit/ad8039a2630226babc9f5e5e2fa0971acfc35dd2))
* **Google Photos - Toolkit:** add real-time operation progress info and protect filter card chevrons ([a3e3ef1](https://github.com/Akash-Sriram/morphe-google-photos/commit/a3e3ef19001c082187298a654b9331071e613329))
* **Google Photos - Toolkit:** add remaining actions, locked folder source, category discovery chips, and collapsible steps ([a49aef0](https://github.com/Akash-Sriram/morphe-google-photos/commit/a49aef0785ff15a76a28e589df501068eedaf572))
* **Google Photos - Toolkit:** auto-refresh preview grid when locked items are unlocked ([5bf9d18](https://github.com/Akash-Sriram/morphe-google-photos/commit/5bf9d1875e7ea7dd3698b727964891c9f4ab1fdf))
* **Google Photos - Toolkit:** auto-unlock selected media upon MarsGridActivity biometric entry ([748803c](https://github.com/Akash-Sriram/morphe-google-photos/commit/748803c090b9f41d9ec0613834f0db252e9b2d4b))
* **Google Photos - Toolkit:** compact UI revamp with collapsible filters, active chips, and prominent preview ([dacc366](https://github.com/Akash-Sriram/morphe-google-photos/commit/dacc36677caa5a59b596a3bb2103990a1eff83f7))
* **Google Photos - Toolkit:** directly dispatch move out of locked folder without confirmation dialog and auto-return ([0e026b4](https://github.com/Akash-Sriram/morphe-google-photos/commit/0e026b4e6fc343729b2035bd02ed09c059e79b85))
* **Google Photos - Toolkit:** dynamic sorting, photo inspector & compare modal, batch DCIM export, and AI model manager ([50931df](https://github.com/Akash-Sriram/morphe-google-photos/commit/50931df181f77e99239d95f763905367e53df041))
* **Google Photos - Toolkit:** implement complete native Google Photos toolkit and cloud album integration ([04d6868](https://github.com/Akash-Sriram/morphe-google-photos/commit/04d68687248536b0136fb1a5ce13b9aa34613170))
* **Google Photos - Toolkit:** implement UI paging with view recycling and custom SQLite partial indices ([606f5e3](https://github.com/Akash-Sriram/morphe-google-photos/commit/606f5e3d62abf04201ceef6e0eb67a90c6ac9d9f))
* **Google Photos - Toolkit:** make Source, Filters, and Actions collapsible with Actions placed above Results ([8bb5e18](https://github.com/Akash-Sriram/morphe-google-photos/commit/8bb5e1803081ff07e6d3f8a7db29122c7c81704b))
* **Google Photos - Toolkit:** modernize UI with grid/list preview toggle, selection, progress bar and debounced live reload ([9fa6fd1](https://github.com/Akash-Sriram/morphe-google-photos/commit/9fa6fd121296fac0056e32f23dcf12ec096ebed3))
* **Google Photos - Toolkit:** native Glide thumbnail loading and enhanced caption/regex filtering ([6200759](https://github.com/Akash-Sriram/morphe-google-photos/commit/6200759c379b4fdd6ee6a54f75570c1f244db3c2))
* **Google Photos - Toolkit:** optimize patch fingerprints, memory caching, and fix library scan & album removal ([20668b8](https://github.com/Akash-Sriram/morphe-google-photos/commit/20668b8f9cb9c174aca954c3c35504524a9f8fc5))
* **Google Photos - Toolkit:** port online DEX tasks, resolve MoveToTrash crash, and fix filter NPEs ([4e1c08b](https://github.com/Akash-Sriram/morphe-google-photos/commit/4e1c08be27dcbdf6ea971be56266371a8d291615))
* **Google Photos - Toolkit:** support Google Photos 7.97.0 across all build variants and add dynamic TaskRunner resolution ([82fe84e](https://github.com/Akash-Sriram/morphe-google-photos/commit/82fe84ef22a5a62ab58e83be2be069a076635796))
* **Google Photos - Toolkit:** wire unlock action to launch Locked Folder biometric intent ([899da3e](https://github.com/Akash-Sriram/morphe-google-photos/commit/899da3eccfb41c6690829409288b99babf4f620d))

### 🔧 Improvements

* **Google Photos:** optimize patch build times, app startup, database batch transactions, and visual similarity threading ([1d54c29](https://github.com/Akash-Sriram/morphe-google-photos/commit/1d54c298e6bc5ea6418703f878104efc01d8d628))
* **Google Photos:** prevent static view leaks and evict thumbnail cache on dialog dismiss ([cd6932c](https://github.com/Akash-Sriram/morphe-google-photos/commit/cd6932c32cca4f46d6610a4073df0d59ae354ef6))

## [1.14.4](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.14.3...v1.14.4) (2026-10-12)

### 🐛 Bug Fixes

* **Google Photos - Backup:** initialize missing notification channels on startup to prevent JobScheduler crash ([1ee0725](https://github.com/Akash-Sriram/morphe-google-photos/commit/1ee072577222a1e59fcebfcf624174daacecc3a4)), closes [#32](https://github.com/Akash-Sriram/morphe-google-photos/issues/32)

## [1.14.3](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.14.2...v1.14.3) (2026-10-09)

### 🐛 Bug Fixes

* **Google Photos - Localcreationdownloader:** Samsung specific optimization for local story and creation downloads ([aae1dc0](https://github.com/Akash-Sriram/morphe-google-photos/commit/aae1dc092c5d1b135290c646c621030904d008fc))

## [1.14.2](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.14.1...v1.14.2) (2026-10-08)

### 🐛 Bug Fixes

* **Google Photos - Localcreationdownloader:** transition hero creation button from saving to saved state ([f2c08c8](https://github.com/Akash-Sriram/morphe-google-photos/commit/f2c08c8e9d61e96d20737a209e73b546f454b07c))

## [1.14.1](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.14.0...v1.14.1) (2026-10-08)

### 🐛 Bug Fixes

* **Google Photos - Seeder:** prevent PhotosModelSeeder from overwriting phenotype flags ([4307a0f](https://github.com/Akash-Sriram/morphe-google-photos/commit/4307a0f1fe8b904822cb11e0b7054f92bbf8df08))

## [1.14.0](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.13.4...v1.14.0) (2026-10-08)

### 🐛 Bug Fixes

* **Google Photos - Deps:** bump ip-address to 10.7.1 to resolve CVE-2026-101912 ([22b2382](https://github.com/Akash-Sriram/morphe-google-photos/commit/22b23823879541722763f7f7f036c3c88b09c884))
* **Google Photos - Deps:** bump postcss-selector-parser, http-cache-semantics, brace-expansion, undici to resolve Dependabot alerts ([db857be](https://github.com/Akash-Sriram/morphe-google-photos/commit/db857be6561a75a631ebd00c3e9ef999a3372776))
* **Google Photos - Localcreationdownloader:** add video/animation export support and fix MFY state flow update ([a76113a](https://github.com/Akash-Sriram/morphe-google-photos/commit/a76113a63f0f6a19ac7dfec6fd8a8aa859bd4156))
* **Google Photos - Localcreationdownloader:** fix mark as saved state tracking for Create tab hero cards ([1dc1ce5](https://github.com/Akash-Sriram/morphe-google-photos/commit/1dc1ce59c31d20b8843c2d9f7108a1324835e3bb))
* **Google Photos - Localcreationdownloader:** remove Lamfd read-state hooks to prevent auto-downloading memories on view ([c58c3bd](https://github.com/Akash-Sriram/morphe-google-photos/commit/c58c3bd18d54562b9022f44ac6af19d8033bb086))
* **Google Photos - Localcreationdownloader:** remove toast popup for silent creation saves ([e2cf941](https://github.com/Akash-Sriram/morphe-google-photos/commit/e2cf941a0038a5476e523fc80a48ad78ba52bf10))

### ✨ New Features

* **Google Photos - Compat:** omit explicit versionCodes restriction from target specification ([2b7afd2](https://github.com/Akash-Sriram/morphe-google-photos/commit/2b7afd21c2912507760193f8b7c41dd4c6983449))
* **Google Photos - Compat:** update target specification to template latest spec with SupportedAbi and ApkFileType ([d9cdb89](https://github.com/Akash-Sriram/morphe-google-photos/commit/d9cdb89998913b3d218af6d54c30fe5ce127adf6))
* **Google Photos - Creations:** divert Create tab and Made-For-You saves to local storage with EXIF timestamps ([d29fb88](https://github.com/Akash-Sriram/morphe-google-photos/commit/d29fb8873f43054f488a0cee7e8be38282a27585))
* **Google Photos - Flags:** reorganize curated flags registry and unify stories and memories category ([7decf63](https://github.com/Akash-Sriram/morphe-google-photos/commit/7decf63547795d83b0ce7f7f07fbbb87e8097f62))
* **Google Photos:** add Google Photos 7.96.0 support, update patch availability, and upgrade build tooling ([2ec8d06](https://github.com/Akash-Sriram/morphe-google-photos/commit/2ec8d06b812dfae3a805d707fb412ebff4cc35ec))
* **Google Photos:** embed EXIF timestamp, suppress duplicate creation downloads, and add 5s Logs long-press reset ([e939902](https://github.com/Akash-Sriram/morphe-google-photos/commit/e939902396424480cd192ac4dbd2c12d2fcf35a8))

## [1.13.4](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.13.3...v1.13.4) (2026-10-04)

### 🐛 Bug Fixes

* **Google Photos - Patches:** match official collage naming, local wall-clock timestamp, and permanent save state ([d370f79](https://github.com/Akash-Sriram/morphe-google-photos/commit/d370f79222f71bd1c9649cbd4f3ea1eaf7633e37))

## [1.13.3](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.13.2...v1.13.3) (2026-10-04)

### 🐛 Bug Fixes

* **Google Photos:** remove toast popups from local creation downloader ([11b28bc](https://github.com/Akash-Sriram/morphe-google-photos/commit/11b28bc29143e30d69e9295e5f759b3f6ec450a0))
* **Google Photos:** save collages to DCIM/Google Photos and upgrade Fife URL quality ([2fa4468](https://github.com/Akash-Sriram/morphe-google-photos/commit/2fa4468732b202794070a6cbc51f2298088c8d4f))

### ✨ New Features

* **Google Photos:** add local creation downloader for quota-free memory collages ([b3db13b](https://github.com/Akash-Sriram/morphe-google-photos/commit/b3db13b6fa0c4c9bb15707e5e48e036df7c06586))
* **Google Photos:** hide save button when saved locally and prevent duplicate downloads ([6f81101](https://github.com/Akash-Sriram/morphe-google-photos/commit/6f8110154ae3f664ca110a433d8db55396039a5a))

## [1.13.2](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.13.1...v1.13.2) (2026-10-04)

### 🐛 Bug Fixes

* **Google Photos - Avatar:** restore active account from native preferences to prevent startup flicker ([585cdf0](https://github.com/Akash-Sriram/morphe-google-photos/commit/585cdf05b7096f4bc0fc4be0bba6a50cc6725701))
* **Google Photos - Branding:** realign pinwheel logos and recolor collapse lottie animation ([4078c4b](https://github.com/Akash-Sriram/morphe-google-photos/commit/4078c4b5c55c72382003cfe93daedadf8ae6fdf3))
* **Google Photos - Gms:** restrict scroll offset injection to static non-RecyclerView methods ([549f918](https://github.com/Akash-Sriram/morphe-google-photos/commit/549f9184900e64cf529ca1906f3f95e48ec042cc))

## [1.13.1](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.13.0...v1.13.1) (2026-10-03)

### 🚀 Updated App Support

* **Google Photos:** promote 7.95.0.989626323 as sole supported version ([0aa6704](https://github.com/Akash-Sriram/morphe-google-photos/commit/0aa6704ad3b2cc6cbbcf66577c604a0a65cba1b6))

## [1.13.0](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.12.2...v1.13.0) (2026-10-03)

### 🐛 Bug Fixes

* **Google Photos - Diagnostics:** disable text selection in log viewer and upgrade logcat reader to zero-drop buffered pipeline ([01d36f8](https://github.com/Akash-Sriram/morphe-google-photos/commit/01d36f872558a66577431b78c07bd9204083c487))
* **Google Photos - Flags:** respect theme, fix search filter expand/collapse, and disallow multiline in search bar ([06289d5](https://github.com/Akash-Sriram/morphe-google-photos/commit/06289d5b44f68312a309b6dfd35981b07193c898))
* **Google Photos:** handle missing MicroG location permission and resolve avatar layout loop ([#25](https://github.com/Akash-Sriram/morphe-google-photos/issues/25)) ([205ba91](https://github.com/Akash-Sriram/morphe-google-photos/commit/205ba915719ca626d5236a07b51be428640c84b5))

### ✨ New Features

* **Google Photos - Flags:** scope enable/disable all to active list and tab, implement reset/load preset lifecycle, and scope category toggle ([083bf00](https://github.com/Akash-Sriram/morphe-google-photos/commit/083bf00dfbe98d405ee3054bed0fbdcaf67823ae))
* **Google Photos - Map:** fix explore map photo count recalculation and grid clipping on location center ([3df096f](https://github.com/Akash-Sriram/morphe-google-photos/commit/3df096faeb063d6c64b563eb4409e1e2d9b2515a))
* **Google Photos:** add custom flags tab with individual/bulk clear, fix category enable/disable logic, update restart icon, and unify diagnostics to single view ([60f4e32](https://github.com/Akash-Sriram/morphe-google-photos/commit/60f4e32a816dfec51eeb461328a215a2479584ae))
* **Google Photos:** redesign diagnostics dialog to M3, fix flag manager dialog rounding, and remove duplicate logs menu item ([b8ea57b](https://github.com/Akash-Sriram/morphe-google-photos/commit/b8ea57bf7bbab4e098c0ba9aabc8653c03c50e97))
* **Google Photos - Ui:** redesign session selector with M3 picker, add expand/collapse quick pills, and enable Google One Bento Badge ([cb130b6](https://github.com/Akash-Sriram/morphe-google-photos/commit/cb130b6b0e9c008ba106acd2fa7d48498b692ca6))
* **Google Photos - Ui:** redesign settings dock and flag manager with native Material 3 styling and vector icons ([91eec8d](https://github.com/Akash-Sriram/morphe-google-photos/commit/91eec8dd42fa33589f17b91712d617b7d79c86cc))
* **Google Photos - Ui:** tailor-made Material vector icons, meaningful category names, and 24dp curves ([ae0246a](https://github.com/Akash-Sriram/morphe-google-photos/commit/ae0246a856df9df9ba078d8a2ec9214aebb83844))

## [1.12.2](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.12.1...v1.12.2) (2026-09-26)

### 🐛 Bug Fixes

* **Google Photos - Flags:** prevent empty search state when categories are collapsed without query ([0db0ccb](https://github.com/Akash-Sriram/morphe-google-photos/commit/0db0ccb7a738c76a6e7b008eb43ebc96d0b8f4b5))

## [1.12.1](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.12.0...v1.12.1) (2026-09-26)

### 🐛 Bug Fixes

* **Google Photos - Flags:** collapse categories by default and align scrollbar outside UI ([0ab74a6](https://github.com/Akash-Sriram/morphe-google-photos/commit/0ab74a6f90e9356da078502a682ac79faec96716)), closes [#22](https://github.com/Akash-Sriram/morphe-google-photos/issues/22) [#24](https://github.com/Akash-Sriram/morphe-google-photos/issues/24) [#22](https://github.com/Akash-Sriram/morphe-google-photos/issues/22) [#24](https://github.com/Akash-Sriram/morphe-google-photos/issues/24)

## [1.12.0](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.11.0...v1.12.0) (2026-09-26)

### ✨ New Features

* **Google Photos - Flags:** add collapsible headers and fix preset dialog styling ([9802906](https://github.com/Akash-Sriram/morphe-google-photos/commit/980290640016559a0852fac18095421deb555b81))

## [1.11.0](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.10.1...v1.11.0) (2026-09-26)

### 🐛 Bug Fixes

* **Google Photos - Flags:** fix curated flag default state, active counter, and deduplicate custom overrides ([bf07950](https://github.com/Akash-Sriram/morphe-google-photos/commit/bf07950d9b75bebc4b76f5a72e3275783a9e8407))
* **Google Photos - Flags:** strip inline comments/quotes and add self-healing sanitization for contaminated flag values ([8f67cc4](https://github.com/Akash-Sriram/morphe-google-photos/commit/8f67cc44f5b2876fa446901f99bda82127a6eb57))
* **Google Photos - Maps:** synchronize photo carousel and fix current location FAB toggle in Map Explore ([dadcbed](https://github.com/Akash-Sriram/morphe-google-photos/commit/dadcbed7e9f8cc982049000c187b68a66c4817c4))

### ✨ New Features

* **Google Photos - Compat:** add 7.94.0.984908898 to experimental support ([a4ff34f](https://github.com/Akash-Sriram/morphe-google-photos/commit/a4ff34f3747c2e2aeca5d818b702229330417b50))
* **Google Photos - Diagnostics:** add in-app session log manager, crash reporting, and 1-tap sharing tool ([508aaba](https://github.com/Akash-Sriram/morphe-google-photos/commit/508aaba09e3c0200bbbf97e0458933d770296ed6))
* **Google Photos - Flags:** add complete 8 Creator Tab tools profile merged with user flags ([b0a9f60](https://github.com/Akash-Sriram/morphe-google-photos/commit/b0a9f607d2e32a93b6a2245f850e36d2f5ccaf7a))
* **Google Photos - Flags:** feed all 362 curated Morphe flags as default preset ([f25b284](https://github.com/Akash-Sriram/morphe-google-photos/commit/f25b284df9db2c7710009653b62377b5ba90b9f7))
* **Google Photos - Flags:** implement tool-centric group headers with Enable All batch toggles and trigger badges ([d9ae58e](https://github.com/Akash-Sriram/morphe-google-photos/commit/d9ae58ee82490ab41bd478115b6468fcb9619e9f))
* **Google Photos - Flags:** keep empty slate clean, move load presets to 3-dot menu as placeholder ([13e3f54](https://github.com/Akash-Sriram/morphe-google-photos/commit/13e3f542c14c5a29f5782f05426be8b576ea1bf4))
* **Google Photos - Flags:** only display configured flags, showing clean empty slate upon clearing ([1c94e7c](https://github.com/Akash-Sriram/morphe-google-photos/commit/1c94e7cbeaa5963f6c2826320c7c2ac55f95f458))
* **Google Photos - Flags:** redesign Flag Manager with single view, SAF file picker, and bulk paste ([e87d422](https://github.com/Akash-Sriram/morphe-google-photos/commit/e87d422d8247385f84cdfbef26ee3af5f400f19c))
* **Google Photos - Flags:** support all flag types and wire dynamic OneGoogle protobuf bridge ([355cde3](https://github.com/Akash-Sriram/morphe-google-photos/commit/355cde3a89576d9af4d04209eb8b1c6789c2f864))
* **Google Photos:** add Google One Bento Badge patch to restore genuine Pro badge on MicroG ([483c41f](https://github.com/Akash-Sriram/morphe-google-photos/commit/483c41f319669bb86476ef007720ef7881f99875))
* **Google Photos - Morphe:** strip preloaded flags, load 568 flags from morphe_photos_flags.txt ([2139ad4](https://github.com/Akash-Sriram/morphe-google-photos/commit/2139ad48fdf32443f6294155c6285d61a7ffcb95))

### 🔧 Improvements

* **Google Photos - Flags:** optimize Flag Manager for 1k+ flags with virtualized ListView, debounced search, and async stream import ([1f287b0](https://github.com/Akash-Sriram/morphe-google-photos/commit/1f287b058578ee752bfe9f3703bc89f18b7ce230))

## [1.10.1](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.10.0...v1.10.1) (2026-09-23)

### 🚀 Updated App Support

* **Google Photos:** promote Google Photos v7.93.0.982110057 to stable target ([01366bc](https://github.com/Akash-Sriram/morphe-google-photos/commit/01366bca5d3e6a3c6eae1c0483ced6e3bb0f21ab))

## [1.10.0](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.9.0...v1.10.0) (2026-09-23)

### 🐛 Bug Fixes

* **Google Photos:** parallelize model downloads and resolve startup freezes ([99287f6](https://github.com/Akash-Sriram/morphe-google-photos/commit/99287f67ad97a13d4e1b61cf71b3cd65e36e0e50))

### ✨ New Features

* **Google Photos:** add deviceComplianceCheckPatch to suppress Play Protect screen ([5613eae](https://github.com/Akash-Sriram/morphe-google-photos/commit/5613eae789aa0cbc37686817fbfd11ccfe754338)), closes [MorpheApp/morphe-patches#2977](https://github.com/MorpheApp/morphe-patches/issues/2977) [#13](https://github.com/Akash-Sriram/morphe-google-photos/issues/13)

## [1.9.0](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.8.0...v1.9.0) (2026-09-22)

### ✨ New Features

* **Google Photos - Branding:** add Custom Morphe branding patch and 46 assets ([38f188e](https://github.com/Akash-Sriram/morphe-google-photos/commit/38f188e795db2525aed643eb3df6111d6469091c))

## [1.8.0](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.7.0...v1.8.0) (2026-09-22)

### 🐛 Bug Fixes

* **Google Photos:** repair map binder resolution, Mapbox camera fly-to, and gate ML model seeder ([1ea95c4](https://github.com/Akash-Sriram/morphe-google-photos/commit/1ea95c4b135e64cf2307043a716124c35f789cb8))

### ✨ New Features

* **Google Photos:** remove in-app update checker from base patches repository ([4e8d94b](https://github.com/Akash-Sriram/morphe-google-photos/commit/4e8d94b69f09072bb4e82ce03af30cacdfecff37))

## [1.7.0](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.6.2...v1.7.0) (2026-09-21)

### ✨ New Features

* **Google Photos:** add AMOLED dark theme patch (closes [#12](https://github.com/Akash-Sriram/morphe-google-photos/issues/12)) ([e4cbe58](https://github.com/Akash-Sriram/morphe-google-photos/commit/e4cbe58e49fd3b7df95bcb50a728ddfa196d9900))

## [1.6.2](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.6.1...v1.6.2) (2026-09-21)

### 🐛 Bug Fixes

* **Google Photos - Updater:** add in-app update checker and normalize changelog scopes for Morphe Manager ([64fcd6e](https://github.com/Akash-Sriram/morphe-google-photos/commit/64fcd6eb33902a6144981e1c05b8f11930344b73))
* **Google Photos - Updater:** add REQUEST_INSTALL_PACKAGES permission and handle SecurityException ([05fed19](https://github.com/Akash-Sriram/morphe-google-photos/commit/05fed1972aceb62b57e1cd386894ff5364b2a382))

## [1.6.1](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.6.0...v1.6.1) (2026-09-21)

### 🐛 Bug Fixes

* **Google Photos:** repair map live location tracking and dynamic Mars provider authority [skip ci] ([4cddf8f](https://github.com/Akash-Sriram/morphe-google-photos/commit/4cddf8fbd4f25fc4811555133f661af23f7f6e8e))

## [1.5.1](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.5.0...v1.5.1) (2026-09-20)

### 🐛 Bug Fixes

* **Google Photos:** remove hardware and id spoofing to prevent Play Protect certification errors ([1fea300](https://github.com/Akash-Sriram/morphe-google-photos/commit/1fea300a0c3bc46c36d87297432e51949be52850))

## [1.5.0](https://github.com/Akash-Sriram/morphe-google-photos/compare/v1.4.1...v1.5.0) (2026-09-19)

### 🐛 Bug Fixes

* **Google Photos - Flags:** purge obsolete 3024 and legacy memory flags, align 3999 to official dump ([e729331](https://github.com/Akash-Sriram/morphe-google-photos/commit/e72933185457d609d3ca07ab8fba03380db98d26))
* **Google Photos - Flags:** purge truncated 4-digit flags, use verified 8-digit keys, and ensure synchronous persistence ([f6c0d66](https://github.com/Akash-Sriram/morphe-google-photos/commit/f6c0d666be8572a2959ee3dcd1ec7440a93da6c9))
* **Google Photos - Flags:** sync recipe toggle state bidirectionally with Curated Flags and write zero-values on disable ([97b0788](https://github.com/Akash-Sriram/morphe-google-photos/commit/97b0788457f2a97b8aea116e861d90b659d4fbe6))
* **Google Photos:** align story cutouts, system fonts, OneGoogle avatar, and seed data ([4e294c2](https://github.com/Akash-Sriram/morphe-google-photos/commit/4e294c28fc8dd65e8830f60a3ffff1c49d00cf17))
* **Google Photos:** resolve Locked Folder cloud backup on mod package name ([02caf09](https://github.com/Akash-Sriram/morphe-google-photos/commit/02caf093742bc5b273fc0a178163e05139471c6c))
* **Google Photos - Ui:** dynamic flag count in reset menu, now shows correct 29 instead of stale 26 ([0821a8a](https://github.com/Akash-Sriram/morphe-google-photos/commit/0821a8a1bbd32bf982c0186af424f749ed583e9e))

### ✨ New Features

* **Google Photos - Flags:** add 45709528 (Enhanced Pixel Processing) to curated flags, updating total to 25 ([0e6466f](https://github.com/Akash-Sriram/morphe-google-photos/commit/0e6466f32af3e38ca6e8f9d9fa99ae14f6f95fb4))
* **Google Photos - Flags:** add curated flags table and refine patch definitions ([8c0e159](https://github.com/Akash-Sriram/morphe-google-photos/commit/8c0e1599ae8dea90e5c624f2839eabdde83cf72b))

## [1.4.1](https://github.com/Akash-Sriram/De-Vanced/compare/v1.4.0...v1.4.1) (2026-09-18)

### 🐛 Bug Fixes

* **Google Photos - Ci:** prevent gradlew.bat line ending changes from failing backmerge ([3af9529](https://github.com/Akash-Sriram/De-Vanced/commit/3af95291e23708bce7e7153cb9f3384ff10b0aa2))

## [1.4.0](https://github.com/Akash-Sriram/De-Vanced/compare/v1.3.1...v1.4.0) (2026-09-18)

### 🐛 Bug Fixes

* **Google Photos - Ci:** remove duplicate release note generation from semantic-release ([4d6093e](https://github.com/Akash-Sriram/De-Vanced/commit/4d6093eb7ef2cfafca26ce6c63f8dbf4b1f2d7bc))
* **Google Photos:** catch Throwable and skip framework views during avatar reflection ([6aa80b8](https://github.com/Akash-Sriram/De-Vanced/commit/6aa80b8c562d773b4aa46d61dd593e180fe2605b))
* **Google Photos:** restore global background model seeder and clean readiness gates ([336e7f5](https://github.com/Akash-Sriram/De-Vanced/commit/336e7f59875d62a8f423f23fe8ba54c2c65bb6cd))

### ✨ New Features

* **Google Photos:** align with latest patch template and refine photo patches ([8b6468a](https://github.com/Akash-Sriram/De-Vanced/commit/8b6468a1194f19a9dbce20fdd762f3bd4027a68c))

## [1.3.1](https://github.com/Akash-Sriram/De-Vanced/compare/v1.3.0...v1.3.1) (2026-09-16)


### Bug Fixes

* **Google Photos:** bypass GmsCore battery optimization and background check dialogs ([24e3a1e](https://github.com/Akash-Sriram/De-Vanced/commit/24e3a1ebc1def251d36abfa856e23cd1ebbe5c96))

# [1.3.0](https://github.com/Akash-Sriram/De-Vanced/compare/v1.2.3...v1.3.0) (2026-09-15)


### Features

* **Google Photos:** dynamic ML model discovery and download from MDD manifests ([e39c700](https://github.com/Akash-Sriram/De-Vanced/commit/e39c700c7a19173fcf5c2a848afbdd3c8cddc389))

## [1.2.3](https://github.com/Akash-Sriram/De-Vanced/compare/v1.2.2...v1.2.3) (2026-09-13)


### Bug Fixes

* **Google Photos - Google Photos V7.92:** Restore ML Models and Fix Story Crashes ([9fa2350](https://github.com/Akash-Sriram/De-Vanced/commit/9fa23505e4c76790da39d84ae18035dd1d1f168b))
* **Google Photos:** revert crashy dynamic story fingerprinting and separate model readiness gates safely ([e34f695](https://github.com/Akash-Sriram/De-Vanced/commit/e34f695c4200ace18840cdb8a5ea2cba8af48e01))
* **Google Photos:** use dynamic fingerprinting for memory style flags ([62bc026](https://github.com/Akash-Sriram/De-Vanced/commit/62bc026b4cf2320f6a017ec80688a15361e27ece))

## [1.2.2](https://github.com/Akash-Sriram/De-Vanced/compare/v1.2.1...v1.2.2) (2026-09-13)


### Bug Fixes

* **Google Photos:** unconditionally report models as loaded to fix 0MB bug for Moods suite ([c032335](https://github.com/Akash-Sriram/De-Vanced/commit/c0323359bc4aa4ee2f870ca89681c10fe5364dd0))

## [1.2.1](https://github.com/Akash-Sriram/De-Vanced/compare/v1.2.0...v1.2.1) (2026-09-13)


### Bug Fixes

* **Google Photos:** bump REMOTE_MODELS_URL to v1.1-models to support 72 models ([8dfc0c3](https://github.com/Akash-Sriram/De-Vanced/commit/8dfc0c329d8e7949de904c59ae901d27ed752735))
* **Google Photos:** remove leftover Google CDN fallback from seeder ([2022eaf](https://github.com/Akash-Sriram/De-Vanced/commit/2022eaf1c6f062f49b38f4965f272db9359e6e47))

# [1.2.0](https://github.com/Akash-Sriram/De-Vanced/compare/v1.1.0...v1.2.0) (2026-09-13)


### Features

* **Google Photos:** add dynamic auto-fingerprinting and v7.92 support for memory style flags ([8bd398c](https://github.com/Akash-Sriram/De-Vanced/commit/8bd398c50093da46f594f6bd7b8f02a845f93ee4))
* **Google Photos:** expand neural model pack to 72 models with Moods suite ([764ff87](https://github.com/Akash-Sriram/De-Vanced/commit/764ff87a822e1e550cecd4526a52da26c8f99ac1))

# [1.1.0](https://github.com/Akash-Sriram/De-Vanced/compare/v1.0.0...v1.1.0) (2026-09-13)


### Features

* **Google Photos:** port SystemProperties spoofing to cover native C/C++ property reads ([605e4cb](https://github.com/Akash-Sriram/De-Vanced/commit/605e4cb5451379f02370b503df4451dbade37774))

# 1.0.0 (2026-09-12)


### Bug Fixes

* **Google Photos:** avoid forcing Photos frictionless login ([9cb1e11](https://github.com/Akash-Sriram/De-Vanced/commit/9cb1e117551bf759c11163417cef160b3f9bac37))
* **Google Photos:** change googlephotos extension to base shared and remove unused cert pinning patch ([6c93248](https://github.com/Akash-Sriram/De-Vanced/commit/6c93248de1039ede74d92af8d4d18f4ce1b44787))
* **Google Photos - Ci:** restore semantic-release rules so feat, fix, and release commits trigger versions ([3728b97](https://github.com/Akash-Sriram/De-Vanced/commit/3728b978519854968a56847075aa9d58565a85e3))
* **Google Photos - Flags:** prevent auto-reseeding defaults on clear-all and enforce high-contrast dialog buttons ([bbcb716](https://github.com/Akash-Sriram/De-Vanced/commit/bbcb7160ea420512e23ce87b0e5a8d6f6d23dcc2)), closes [hi#contrast](https://github.com/hi/issues/contrast)
* **Google Photos:** google photos patch logic ([58f8bc8](https://github.com/Akash-Sriram/De-Vanced/commit/58f8bc80dfd601cfe5a5dfee81e64d78bab0f496))
* **Google Photos:** enable complete Maps SDK multi-DEX routing and MicroG-RE VTM support ([c6ce1b5](https://github.com/Akash-Sriram/De-Vanced/commit/c6ce1b5b1ec6a8aae48a914617fbd2f901165e03))
* **Google Photos:** fix Map Explore current location FAB and persistent location puck ([50386ec](https://github.com/Akash-Sriram/De-Vanced/commit/50386ecc07672f1ca1eebed30855b81642748955))
* **Google Photos:** isolate Phenotype from GmsCore wipe and fix HomeActivity startup lifecycle hook ([26c03a9](https://github.com/Akash-Sriram/De-Vanced/commit/26c03a94c243504330c01d3ce22f52d95de82b6a))
* **Google Photos:** make legacy check optional to prevent matching errors ([ffcf044](https://github.com/Akash-Sriram/De-Vanced/commit/ffcf0447961f9f4c0e214ec0c9ded7e7e775a4e6))
* **Google Photos:** prevent NullPointerException in BackupNotificationManager during local media sync scheduling ([7b481ae](https://github.com/Akash-Sriram/De-Vanced/commit/7b481ae5e61e2310c8f7813036bfda8f8194b4cc))
* **Google Photos:** resolve classcast exception in smali string injection ([669f375](https://github.com/Akash-Sriram/De-Vanced/commit/669f37545acc687eb00d0fc2fe7d2fe749c34a7d))
* **Google Photos:** resolve DCIM backup control patch compatibility issues ([8096849](https://github.com/Akash-Sriram/De-Vanced/commit/8096849b16c29fd04600fe1e830aeea5d09cad1f))
* **Google Photos:** resolve HomeActivity onCreate fingerprint and remove redundant constructor hooks ([bbf7a21](https://github.com/Akash-Sriram/De-Vanced/commit/bbf7a21bba34453ca9e6fe9d06f1f304078c7af3))
* **Google Photos:** silence avatar fetch error toasts and avoid redundant build-number update dialogs ([63aa7b4](https://github.com/Akash-Sriram/De-Vanced/commit/63aa7b4e5d6002d9780ef374e45ea562b92a879e))
* **Google Photos:** use single userinfo.profile scope and support direct avatar fallback from accountManager ([a3bfb5a](https://github.com/Akash-Sriram/De-Vanced/commit/a3bfb5a746decfbe019c7968c56e509d01e5ed81))
* **Google Photos:** inline changePackageNamePatch as private implementation to hide it ([0976e55](https://github.com/Akash-Sriram/De-Vanced/commit/0976e55733cf04a58ee1c4fc3270b7e3d7affbe5))
* **Google Photos:** make Google Photos spoof build patch self-contained ([7b431dd](https://github.com/Akash-Sriram/De-Vanced/commit/7b431dd5a707231b5289db60a6fdee5d24b27832))
* **Google Photos:** make gradlew executable ([3be2ec5](https://github.com/Akash-Sriram/De-Vanced/commit/3be2ec51fff5990c174c166e7a02625971832063))
* **Google Photos - Phenotype:** use theme-independent button backgrounds and high contrast text for Samsung OneUI & AOSP ([2c9e9fe](https://github.com/Akash-Sriram/De-Vanced/commit/2c9e9fe6d04860f652b48c5a4f597d5b4f34010b))
* **Google Photos:** fix editor launch by reverting unnecessary package name string rewrites and finalize phenotype UI defaults ([c29f936](https://github.com/Akash-Sriram/De-Vanced/commit/c29f9369760aad5b8f52ba030b328f7a114ac554))
* **Google Photos:** handle signed-out state and support avatar in account switcher list ([f7c82ee](https://github.com/Akash-Sriram/De-Vanced/commit/f7c82ee420526db3c10a721fcac6f3c915e00ecc))
* **Google Photos:** remove duplicate Morphe Photos compatibility target ([7099408](https://github.com/Akash-Sriram/De-Vanced/commit/70994082e501851e7a362dfefc644b8a1d593a6d))
* **Google Photos:** render avatar in clean circle with anti-aliased border ring ([5411a4e](https://github.com/Akash-Sriram/De-Vanced/commit/5411a4eac78e9e4a22757ff34ecf114e5be464d0))
* **Google Photos:** resolve avatar loading with openid scope and dynamic view binding ([18315d8](https://github.com/Akash-Sriram/De-Vanced/commit/18315d89db51ded1a255395963de50695db53958))
* **Google Photos:** support onboarding bottom sheet account avatar and multi-depth row binding ([6f8cdaa](https://github.com/Akash-Sriram/De-Vanced/commit/6f8cdaa12bf9c02470e64e5ccadbc74d40e3d782))
* **Google Photos:** release 1.0.1 ([b68b315](https://github.com/Akash-Sriram/De-Vanced/commit/b68b31503e57a9253237a3bf2be8bacfd23f7c86))
* **Google Photos:** release 1.0.1 ([26175cf](https://github.com/Akash-Sriram/De-Vanced/commit/26175cf2910917be08734a741087018e127a1648))
* **Google Photos:** release v1.0.3 with Google Recorder fix ([c03c701](https://github.com/Akash-Sriram/De-Vanced/commit/c03c701a6ba658afb4f4f2093faca9e6bc91e49c))
* **Google Photos:** release v1.0.4 (Photos account persistence + TikTok defaults) ([b04a57e](https://github.com/Akash-Sriram/De-Vanced/commit/b04a57e97d82d8e236e0979ff1069c540380f353))
* **Google Photos:** restore GmsCoreSupportPatch to shared extension module ([ec9ce23](https://github.com/Akash-Sriram/De-Vanced/commit/ec9ce239f2159c5f6219b48a1431663f5a1005de))
* **Google Photos:** stabilize Google Photos GmsCore support ([b3ef0b6](https://github.com/Akash-Sriram/De-Vanced/commit/b3ef0b672fd411d468fc80ca827056266352ba14))


### Features

* **Google Photos - Flags:** set exact default UI preset flags and fix photo editor launch ([235b894](https://github.com/Akash-Sriram/De-Vanced/commit/235b8941bc4f38d14e3a5e75d2a3ec15d6368e8c))
* **Google Photos:** add PhenotypeFlagManager with Import/Export, clean top toolbar and decouple update checker ([171b43e](https://github.com/Akash-Sriram/De-Vanced/commit/171b43eb31fe56153dcebd82dd4065112d3ff328))
* **Google Photos:** add standalone Account avatar patch and decouple GmsCore support ([1008618](https://github.com/Akash-Sriram/De-Vanced/commit/1008618e4ea510c779573ab1914eb36851f2f0b0))
* **Google Photos:** bundle verified floating pill Phenotype XML and hook Application early seeding ([ad3efba](https://github.com/Akash-Sriram/De-Vanced/commit/ad3efbaf4bc2b162295090d9041e8ae67dc733c5))
* **Google Photos:** stabilize non-root support with 2021 feature flags, avatar bridge & media fixes ([507c805](https://github.com/Akash-Sriram/De-Vanced/commit/507c805de8078257006dc2a167e688a8188fa8ab))
* **Google Photos:** add background streaming downloader for non-root model provisioning ([f265b26](https://github.com/Akash-Sriram/De-Vanced/commit/f265b2631a32740a9e3ebb91716ddb4968978850))
* **Google Photos:** add Google Photos avatar bridge and Play Services signature bypass ([#114](https://github.com/Akash-Sriram/De-Vanced/issues/114)) ([9e29ee7](https://github.com/Akash-Sriram/De-Vanced/commit/9e29ee7beaeeb43576a047568debcd44a7b7cad3))
* **Google Photos:** add PhotosModelSeeder for persistent ML model and MDD rehydration ([712ffee](https://github.com/Akash-Sriram/De-Vanced/commit/712ffee068bb4065cbbf6207af2190db8e8bca78))
* **Google Photos:** bundle full 223KB official phenotype flags for modern floating pill navigation ([6977531](https://github.com/Akash-Sriram/De-Vanced/commit/6977531df9f843e7a7867bb2f9cee02c285a2287))
* **Google Photos:** bundle official phenotype flags and auto-seed in-memory on first launch ([d3d112b](https://github.com/Akash-Sriram/De-Vanced/commit/d3d112be46b40daf78b647a0bf01ebddab7e90aa))
* **Google Photos:** embed clean official phenotype flags with dynamic account sync and early application hook ([cb6c752](https://github.com/Akash-Sriram/De-Vanced/commit/cb6c7520e770148b26b5a80634bd991d3c44e8a2))
* **Google Photos:** enable all Google Photos patches by default and prune obsolete legacy patches ([e6f9655](https://github.com/Akash-Sriram/De-Vanced/commit/e6f965520fa9fa0f1984ffe5d2789bb72fc4119b))
* **Google Photos:** enable all PIXEL_PRELOAD and PIXEL_EXPERIENCE features ([d2ecdf4](https://github.com/Akash-Sriram/De-Vanced/commit/d2ecdf41a7eaa015881022b1a17506ac8e4be55b))
* **Google Photos:** enable modern Pixel experience features across multi-DEX for redesigned Collections UI ([ad26f7e](https://github.com/Akash-Sriram/De-Vanced/commit/ad26f7eac45e52dab53c4949d76d7955f2d30617))
* **Google Photos:** hard-bake memory style flags and local font fallback for scrapbook cutouts ([5f72625](https://github.com/Akash-Sriram/De-Vanced/commit/5f7262588e0b0f5c9af06136df9f31c29f80c335))
* **Google Photos:** limit default spoof features to Pixel 2016 and add model readiness gates ([d5de929](https://github.com/Akash-Sriram/De-Vanced/commit/d5de929597c681a8d04f24c4e9dadb0ab1eaf084))
* **Google Photos:** pre-fetch avatars on launch and bind account row avatars dynamically ([c921078](https://github.com/Akash-Sriram/De-Vanced/commit/c9210781302fa96c4a3205173d47698c83f26652))
* **Google Photos:** redesign phenotype flag manager with M3 floating modal, live GMS Flags API sync, and unified dialogs ([1b38d88](https://github.com/Akash-Sriram/De-Vanced/commit/1b38d88d78fd417ebb3579fb10c63d84638cf84d))
* **Google Photos:** sync official phenotype flags and optimize flag manager UI ([5cf6026](https://github.com/Akash-Sriram/De-Vanced/commit/5cf60269162a95f8a76a39d42168ec6d274623de))
* **Google Photos:** relax FrictionlessEligibilityFingerprint to support Google Photos 7.87.0+ ([3eef011](https://github.com/Akash-Sriram/De-Vanced/commit/3eef011b82ddaadb745f972ae2e1f4ea2be00b16))
* **Google Photos:** release v1.1.0 ([901b2ec](https://github.com/Akash-Sriram/De-Vanced/commit/901b2ec28fb8e5863d2085d471f0cf529de08422))

# [1.0.0](https://github.com/Akash-Sriram/De-Vanced/releases/tag/v1.0.0) (2026-09-13)

Welcome to the inaugural standalone release of **De-Vanced (Morphe Patches)**! This release consolidates months of advanced research and engineering into a unified, out-of-the-box experience for Google Photos on both rooted and non-rooted devices (including Samsung Galaxy S24, Pixel, OnePlus, Xiaomi, and more).

---

### ✨ Core Highlights & Features

#### 🧠 Neural Model Auto-Seeder & Background Streaming (`PhotosModelSeeder`)
- **Seamless Non-Root Delivery**: Automatically provisions all official TensorFlow Lite ML models required for Google Photos AI editing tools.
- **Moods & Creative Lab Expansion**: Bundled complete 20-asset neural suite (Vision Transformer ToneFix model `vit_tonefix_v2.tflite`, 10 3D LUT profiles, 5 film textures, and 2 light leak overlays) bringing the total bundle to 72 verified models (~336.8 MB) with automated MDD group registration.
- **In-App Background Downloader**: On fresh installs (such as on Samsung Galaxy S24), streams and unzips the verified model pack directly from GitHub Releases CDN with zero user intervention.
- **Clear-Data Immunity**: Simultaneously caches all models and MDD manifests into persistent external storage (`/storage/emulated/0/Android/media/app.morphe.android.apps.photos/`), ensuring models instantly rehydrate after "Clear Data" without needing network access.
- **Model Readiness Smali Gates**: Hard-baked bytecode overrides for `Lanqb`, `Lanrk`, `Laspz`, and `Larea` to report Magic Eraser, Portrait Segmenter, and Sky replacement models as loaded and ready.

#### ☁️ Unlimited Original Quality Backup (Pixel 2016 Spoof)
- **Permanent Unmetered Cloud Storage**: Default feature spoofing set to original Google Pixel (`NEXUS_PRELOAD`) for unlimited photo and video backups at full original quality.
- **Tensor TPU Crash Prevention**: Selectively avoids newer Pixel 8/9 spoof flags (`PIXEL_2023_MIDYEAR_PRELOAD`) that trigger missing `.darwinn` Tensor NPU hardware traps on Qualcomm Snapdragon and Samsung Exynos chipsets.

#### 🎛️ Complete Official Phenotype Flags Engine (2,492 Flags)
- **Zero-Config Feature Parity**: Embeds the full set of 2,492 production phenotype flags (`PhenotypeSeedData.java`), automatically seeded into SharedPreferences on first boot.
- **In-App Phenotype Flag Manager**: Access and customize experimental Google Photos flags directly from the app toolbar with built-in search, toggle overrides, and JSON import/export.
- **Dynamic Google Account Synchronization**: Automatically links the active Google account token across multi-account environments to preserve individual settings.

#### 👤 Dynamic Google Account Avatar Bridge
- **Native Account Switcher Support**: Fetches and renders user Google profile avatars dynamically in circular anti-aliased views across top app bars and account switcher sheets without requiring Google Play Services signature spoofing.

#### 🎨 Scrapbook Memories & Local Font Provider Bypass
- **Offline Font Resolution**: Hard-bakes a local font provider bypass (`BakeMemoryStyleFlagsPatch.kt`) pointing to system `/system/fonts/` (Roboto, Noto Serif), eliminating corrupted cutout text or crashes caused by missing GMS font certificates.
- **Memory Styles**: Fully unlocks modern scrapbooking styles, animated collage templates, and memory music playback.

---

### 📦 Compatibility
- **Target Application**: Google Photos (`com.google.android.apps.photos` / `app.morphe.android.apps.photos`)
- **Supported Versions**: `7.0.0` - `7.92.0+`
- **Supported Android Versions**: Android 8.0 (API 26) through Android 15 (API 35)
- **Supported Architectures**: `arm64-v8a`, `armeabi-v7a`