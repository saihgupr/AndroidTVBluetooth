## 2024-05-22 - [Accessibility] Interactive Cards Lacking Action Descriptions
**Learning:** Interactive list items (cards) used for TV navigation often rely on visual cues (buttons inside) but don't expose the action to screen readers on the container itself.
**Action:** Always set `contentDescription` on the interactive container (itemView) to explicitly describe the action (e.g., "Connect to [Device]") rather than just the state.
