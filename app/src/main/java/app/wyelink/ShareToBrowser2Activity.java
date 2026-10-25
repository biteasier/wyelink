package app.wyelink;

/** Share-sheet target "Open in Browser 2": bounces a page that opened in the wrong browser. */
public class ShareToBrowser2Activity extends RouterActivity {
    @Override
    int forcedTarget() {
        return Router.BROWSER_2;
    }
}
