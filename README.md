# Color World (Fabric, Minecraft 1.21.2)

The world keeps going grayscale. Its colors come back one group at a time, either as their original colors or as random ones, and when 85% of the world's visual color has returned, you die.

## The rules (as built)

| Rule | How it works |
|---|---|
| 45% original / 55% random | Each time a color group returns it rolls once: 45% original colors, 55% a random hue. |
| Both count toward restoration | A group counts as "returned" either way. |
| Death at 85% | The meter is the share of the *color you can see* that belongs to returned groups. Reaching 85% kills you. |
| Grayscale reset every 30 s | Everything goes gray again, the meter drops to 0%, a new cycle starts. |
| Instant transitions | Groups switch in a single frame, no fading. |
| Groups stay consistent | A group is a slice of the hue wheel (red, orange, yellow, green, cyan, blue, purple), decided from each pixel's original hue. All grass, leaves and slime are the same "green" group and get the same treatment everywhere until the next reset. |
| 85% = color, not group count | The meter weights each group by how much color it takes up in view (total chroma of the rendered picture). Two big groups (green + blue) can be 90%; five small ones can be 10%. |

Not specified in the design, so I chose them (all in `config/colorworld.properties`): one group returns every 5 seconds; the meter measures what is on screen, so looking at gray stone lowers it; a view with almost no color reads 0%.

## Building

1. Install JDK 21.
2. In this folder run `./gradlew build` (Windows: `gradlew.bat build`).
3. The mod is `build/libs/colorworld-1.0.0.jar` (not the `-sources` one).
4. Put it in your `mods` folder with Fabric Loader 0.16+ and Fabric API `0.106.1+1.21.2`.

To try it from your dev environment: `./gradlew runClient`.

If Gradle can't find the mappings, open https://fabricmc.net/develop, pick 1.21.2, and copy the `yarn_mappings` value into `gradle.properties`.

## Settings

`config/colorworld.properties` is created on first launch. You can change the reset time, the return interval, the 45/55 split, the 85% threshold and hide the top-left status text.

## Checks that were run

- `tools/run-logic-tests.sh` runs 43 checks of the rules with plain Java, no Minecraft needed (hue groups, 45/55 split, the 85% color-vs-group rule, the 30 s cycle, config).
- The shader compiles and links with vanilla's `post/blit` vertex shader.
- Every Minecraft/Fabric class, method and JSON format used here was looked up in the Yarn 1.21.2 mappings, the Fabric API 1.21.2 sources and the vanilla 1.21.2 assets.

## Not checked

The mod was not compiled or run against Minecraft itself. If the game reports an `InjectionError` at startup, the culprit is `PostEffectProcessorMixin` (it hooks `PostEffectProcessor.render` with the frame-graph signature). If the game starts but the world never goes gray, check `logs/latest.log` for a "post effect" error mentioning `colorworld:color_world`.

## Dedicated servers

The color is measured on your screen, so the client asks the server to carry out the death. The mod must be installed on both sides.
