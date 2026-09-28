# Occluded pink triangle regression

Sanitized puzzle-only fixture from the module's existing failed sample at
2026-09-14 10:11. No account IDs, URLs, tokens, unrelated UI, or device metadata
are included. The original private samples remain outside the project.

- Screen: 1264 x 2649; slider Y: 1786.5; source-left hint: 169.
- Recorded failure: `ambiguous image match score=0.3815 verticalOffset=-29`.
- Original saved source crop: (167,1080,184,637).
- Original saved target crop: (419,1080,732,637).
- Test ROI: (167,1081,984,637), raw RGBA compressed with gzip.
- The 68px unsampled gap between saved crops is filled with a continuous row-wise
  interpolation. It is not recovered image data. The new contour branch samples
  only the real source/target pixels, outside this gap. The extra bottom row is
  white, matching the visible white margin. This construction reproduces the
  recorded legacy failure score and vertical offset.
- The lower bend and slanted outline visually align at about 703px horizontal
  displacement; the regression allows 4px. This annotation is image geometry,
  not a claim of server acceptance or an observed successful automatic gesture.
- The upper target contour is partly covered by help/refresh controls. The new
  fallback requires a thin curved source component, high oriented-edge coverage,
  a distinct peak, small vertical offset, and a target away from search bounds.
- Derived negative controls remove the target, duplicate it at another position,
  replace the source with a straight line, and move the target to the outer search
  boundary. Historical images and assertions remain unchanged.
