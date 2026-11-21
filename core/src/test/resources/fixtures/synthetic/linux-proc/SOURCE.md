# Synthetic /proc tree

Hand-written in the formats documented in proc(5) (`/proc/stat`, `/proc/uptime`,
`/proc/<pid>/{stat,status,cmdline,oom_score_adj}`), shaped like an arm64 Android device with eight
cores. Values are invented. They exercise:

- `1`: init, root, `-1000` adj, cmdline with several arguments.
- `2`, `45`: kthreadd and a kernel worker (empty cmdline, comm with `/` and `-`).
- `1043`: zygote64, cmdline padded with NUL bytes.
- `12345`: an app process (`u0_a123`), comm truncated the way the zygote truncates it.
- `4321`: comm containing spaces and parentheses, a system uid (media).
- `23456`: an isolated process (uid 99012) belonging to Chrome.
- `31337`: only `stat` readable (status, cmdline, oom_score_adj missing).

Replace or complement with real recordings from `scripts/capture-fixtures.sh`.
