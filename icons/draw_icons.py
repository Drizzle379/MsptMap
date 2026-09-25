"""绘制地图左上角两个按钮的图标，直接写入模组的 assets。

用法：python icons/draw_icons.py（需要 Pillow）

规格依 Xaero 自身的图标量得（量法见同目录 README）：白块宽 7~11 px，含右下 1 px 灰影共 8~12 px；
alpha 仅 0 / 255，为硬边像素画，无半透明过渡。因此这里不缩放、不抗锯齿，直接按像素绘制：
白 (255,255,255) + 右下 1 px 投影 (63,63,63)。

改尺寸只改下面的常量；换形状改 bars() / cross()。
"""

from pathlib import Path

from PIL import Image

WHITE = (255, 255, 255, 255)
SHADOW = (63, 63, 63, 255)

TEX = 16          # 贴图边长。GuiTexturedButton 的 textureW/H 也是 16，须同步修改
WHITE_BOX = 11    # 白块边长。Xaero 自身的白块宽 7~11 px，取最大值 11
BAR_W, BAR_GAP = 3, 1        # 柱子宽 / 柱间间隔（间隔被投影填成深灰，充当分隔线）
BAR_HEIGHTS = (5, 8, 11)     # 三根柱子自矮到高，底边对齐
CROSS_ARM = 3                # X 的笔画粗细

# 白块居中：白块 + 右侧 1 px 灰影 = WHITE_BOX + 1，两侧各留该值
INK_OFF = (TEX - (WHITE_BOX + 1)) // 2

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/msptmap/textures/gui"


def bars() -> set[tuple[int, int]]:
    """柱状图：三根实心柱子，底边对齐最后一行。"""
    mask: set[tuple[int, int]] = set()
    x0 = 0
    for height in BAR_HEIGHTS:
        for x in range(x0, x0 + BAR_W):
            for y in range(WHITE_BOX - height, WHITE_BOX):
                mask.add((x, y))
        x0 += BAR_W + BAR_GAP
    return mask


def cross() -> set[tuple[int, int]]:
    """X：两条对角线，笔画 CROSS_ARM px 粗，四条臂顶到白块的四个角。"""
    mask: set[tuple[int, int]] = set()
    half = (CROSS_ARM - 1) / 2
    for y in range(WHITE_BOX):
        for x in range(WHITE_BOX):
            if (abs((x - y) - half) < CROSS_ARM / 2
                    or abs((x + y) - (WHITE_BOX - 1) + half) < CROSS_ARM / 2):
                mask.add((x, y))
    return mask


def render(mask: set[tuple[int, int]]) -> Image.Image:
    """白块 + 每个白像素右侧 / 下方的相邻格作投影（本身是白像素的位置不投影）。"""
    shadow = set()
    for (x, y) in mask:
        for neighbour in ((x + 1, y), (x, y + 1)):
            if neighbour not in mask:
                shadow.add(neighbour)
    image = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    pixels = image.load()
    for (x, y) in shadow:
        pixels[x + INK_OFF, y + INK_OFF] = SHADOW
    for (x, y) in mask:
        pixels[x + INK_OFF, y + INK_OFF] = WHITE
    return image


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, mask in (("scan", bars()), ("close", cross())):
        image = render(mask)
        image.save(OUT / f"{name}.png")
        print(f"{OUT / (name + '.png')}  {image.size}  墨迹 {WHITE_BOX}+1 = {WHITE_BOX + 1} px 宽")
