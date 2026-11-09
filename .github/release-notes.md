# SpotiFree v1.0.1

- Network requests no longer freeze the player: native fetches run in the background, with play and queue commands kept in order
- Service worker blocking is on by default, which saves battery and CPU
- Fixed compressed responses from native requests that could come back unreadable
