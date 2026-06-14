# opencv-visual-assert

Libreria Maven per confronti visuali basati su OpenCV.

## Scope

La libreria riceve immagini gia' prodotte dal progetto chiamante e restituisce risultati strutturati. Non apre browser, non esegue test e non dipende da runner o framework E2E.

## Baseline

Directory baseline supportate:

```text
visual_img
resources/test/visual_img
src/test/resources/visual_img
```

La chiave `checkout-summary` risolve automaticamente:

```text
visual_img/checkout-summary.png
visual_img/checkout-summary.jpg
visual_img/checkout-summary.jpeg
```

## Dipendenza Maven

```xml
<dependency>
    <groupId>io.senti.testing</groupId>
    <artifactId>opencv-visual-assert</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

## Confronto baseline

```java
VisualCompareResult result = VisualAssert.compareScreenshotBytes(
        screenshotBytes,
        "checkout-summary",
        VisualCompareOptions.builder()
                .artifactName("checkout-summary")
                .maxDiffPercent(0.25)
                .pixelTolerance(12)
                .build()
);
```

## Template matching

```java
TemplateMatchResult result = OpenCvVision.findTemplateInScreenshotBytes(
        screenshotBytes,
        "checkout-button-icon",
        TemplateMatchOptions.builder()
                .artifactName("checkout-button-icon")
                .minScore(0.92)
                .build()
);
```

## Output

Directory output predefinita:

```text
target/visual-assert
```

Il progetto chiamante gestisce `passed()`, `found()`, `failureMessage()` e i path degli artifact.
