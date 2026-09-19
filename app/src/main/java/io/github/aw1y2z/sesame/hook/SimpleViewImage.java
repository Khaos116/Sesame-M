package io.github.aw1y2z.sesame.hook;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * 精简版 ViewImage - 仅保留坐标获取和 XPath 查找功能
 */
public class SimpleViewImage {
    public static final String TEXT = "text";
    public static final String CONTENT_DESCRIPTION = "contentDescription";
    
    private final View originView;
    private SimpleViewImage parent;
    private int indexOfParent = -1;
    private SimpleViewImage[] children;
    
    public SimpleViewImage(View originView) {
        this.originView = originView;
    }
    
    /**
     * 获取文本内容
     */
    public String getText() {
        if (originView instanceof TextView) {
            CharSequence text = ((TextView) originView).getText();
            return text != null ? text.toString() : null;
        } else {
            CharSequence contentDesc = originView.getContentDescription();
            return contentDesc != null ? contentDesc.toString() : null;
        }
    }
    
    /**
     * 获取屏幕坐标
     */
    public int[] locationOnScreen() {
        int[] location = new int[2];
        originView.getLocationOnScreen(location);
        return location;
    }
    
    /**
     * 获取X坐标
     */
    public int X() {
        return locationOnScreen()[0];
    }
    
    /**
     * 获取Y坐标
     */
    public int Y() {
        return locationOnScreen()[1];
    }
    
    /**
     * 获取子节点数量
     */
    public int childCount() {
        if (!(originView instanceof ViewGroup)) {
            return 0;
        }
        return ((ViewGroup) originView).getChildCount();
    }
    
    /**
     * 获取指定索引的子节点
     */
    public SimpleViewImage childAt(int index) {
        int count = childCount();
        // 视图树是"活的"：遍历期间宿主可能改动它（节点增删）。越界返回 null，由调用方跳过，
        // 不再抛数组越界——原实现是 count < 0 的死判断，随后的 children[index] 会真的抛出去。
        if (index < 0 || index >= count) {
            return null;
        }
        if (children == null || children.length != count) {
            children = new SimpleViewImage[count];
        }
        SimpleViewImage viewImage = children[index];
        if (viewImage != null) {
            return viewImage;
        }
        View view = ((ViewGroup) originView).getChildAt(index);
        if (view == null) {
            return null;
        }
        viewImage = new SimpleViewImage(view);
        viewImage.parent = this;
        viewImage.indexOfParent = index;
        children[index] = viewImage;
        return viewImage;
    }
    
    /**
     * 获取父节点
     */
    public SimpleViewImage getParentNode() {
        return parent;
    }
    
    /**
     * 获取指定层级的父节点
     */
    public SimpleViewImage getParentNode(int n) {
        if (n == 1) {
            return getParentNode();
        }
        SimpleViewImage parentNode = getParentNode();
        return parentNode != null ? parentNode.getParentNode(n - 1) : null;
    }
    
    /**
     * 获取所有子节点
     */
    public List<SimpleViewImage> getChildren() {
        int count = childCount();
        if (count <= 0) {
            return new ArrayList<>();
        }
        List<SimpleViewImage> ret = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SimpleViewImage child = childAt(i);
            // 树的节点数在遍历期间可能变化，childAt 会返回 null，这里必须跳过：
            // 否则解析器会拿到 null 去调 getType() 直接崩
            if (child != null) {
                ret.add(child);
            }
        }
        return ret;
    }
    
    /**
     * 根据XPath查找单个元素
     */
    public SimpleViewImage xpath2One(String xpath) {
        List<SimpleViewImage> results = io.github.aw1y2z.sesame.hook.SimpleXpathParser.evaluate(this, xpath);
        if (!results.isEmpty()) {
            return results.get(0);
        }
        return io.github.aw1y2z.sesame.hook.SimplePageManager.tryGetTopView(xpath);
    }
    
    /**
     * 获取视图类型
     */
    public String getType() {
        return originView.getClass().getSimpleName();
    }
    
    /**
     * 获取属性值
     */
    public Object attribute(String key) {
        switch (key) {
            case TEXT:
                return getText();
            case CONTENT_DESCRIPTION:
                CharSequence contentDesc = originView.getContentDescription();
                return contentDesc != null ? contentDesc.toString() : null;
            default:
                return null;
        }
    }
    
    // Getter & Setter
    public View getOriginView() {
        return originView;
    }
    
    public int getIndexOfParent() {
        return indexOfParent;
    }
    
    public void setParent(SimpleViewImage parent) {
        this.parent = parent;
    }
    
    public void setIndexOfParent(int indexOfParent) {
        this.indexOfParent = indexOfParent;
    }
}