# opencv-visual-assert

Libreria Maven riusabile per confronti visuali con OpenCV.

La libreria riceve immagini gia' prodotte dal framework chiamante e restituisce un risultato strutturato. Non apre browser, non esegue test e non dipende dal runner del progetto chiamante.

## Baseline

La convenzione predefinita per le baseline e':

```text
src/test/resources/visual_img
```

Sono supportati anche questi percorsi:

```text
visual_img
resources/test/visual_img
src/test/resources/visual_img
```

La chiave `checkout-summary` risolve automaticamente, in ordine, file come:

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

Gli artefatti vengono scritti di default in:

```text
target/visual-assert
```

Il progetto chiamante decide come usare `passed()`, `found()`, `failureMessage()` e i path generati.
