## 2024-05-22 - Polling for Async Service Connection
**Learning:** The codebase used `Thread.sleep()` in a loop to wait for `BluetoothProfile` proxy connection. This introduced unnecessary latency (up to 200ms per command) and blocked a thread.
**Action:** Always prefer `wait()`/`notify()` or `CountDownLatch` for coordinating async service connections to minimize latency and improve responsiveness.
