// ChillSpace Shop — Ad Banner Image Generator
// Generates placehold.co URLs for "generic advertising" style product images.
// Knows nothing about the product dataset — takes no product-specific input.
// Text, background/text color, and font are each chosen independently and
// probabilistically (Math.random()), so results are NOT deterministic and
// will differ on every call/reload.

(function () {
    const AD_TEXTS = [
        "BARGAIN",
        "SALE%3A%0AEVERYTHING+MUST+GO",
        "CLEARANCE",
        "HOT+DEAL",
        "LIMITED+TIME+OFFER",
        "PRICE+DROP",
        "FINAL+SALE",
        "DON%27T+MISS+OUT",
    ];

    // Background/foreground kept as pairs only for readability/contrast —
    // which pair gets picked is independent of which text or font gets picked.
    const COLOR_PAIRS = [
        ["c0392b", "ffffff"],
        ["e67e22", "ffffff"],
        ["111111", "ffed00"],
        ["d81b60", "ffffff"],
        ["1a1a1a", "ff3b30"],
        ["2c3e50", "ffffff"],
        ["8e0000", "ffffff"],
        ["f39c12", "111111"],
    ];

    const FONTS = [
        "lato", "lora", "montserrat", "noto-sans", "open-sans", "oswald",
        "playfair-display", "poppins", "pt-sans", "raleway", "roboto",
        "source-sans-pro",
    ];

    function pick(arr) {
        return arr[Math.floor(Math.random() * arr.length)];
    }

    /**
     * Generate a random advertising-banner placeholder image URL.
     * Purely probabilistic — call it fresh whenever you need an image;
     * it has no memory of what it returned before and no knowledge of
     * which product it's for.
     */
    function generateAdBannerImage(size) {
        size = size || 600;
        const text = pick(AD_TEXTS);
        const [bg, fg] = pick(COLOR_PAIRS);
        const font = pick(FONTS);
        return `https://placehold.co/${size}x${size}/${bg}/${fg}?text=${text}&font=${font}`;
    }

    // Expose globally for use by the app script / index.html
    window.generateAdBannerImage = generateAdBannerImage;
})();
