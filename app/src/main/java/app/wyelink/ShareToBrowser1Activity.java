package app.wyelink;

/** Share-sheet target "Open in Browser 1": bounces a page that opened in the wrong browser. */
public class ShareToBrowser1Activity extends RouterActivity {
    @Override
    int forcedTarget() {
        return Router.BROWSER_1;
    }
}
