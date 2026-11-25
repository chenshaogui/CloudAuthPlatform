package io.gitee.chenshaogui.cloudauth.common.excel.handler;

import cn.idev.excel.metadata.data.DataFormatData;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.write.handler.CellWriteHandler;
import cn.idev.excel.write.handler.SheetWriteHandler;
import cn.idev.excel.write.handler.context.CellWriteHandlerContext;
import cn.idev.excel.write.metadata.holder.WriteSheetHolder;
import cn.idev.excel.write.metadata.holder.WriteWorkbookHolder;
import cn.idev.excel.write.metadata.style.WriteCellStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.math.BigDecimal;

/**
 * 全文本导出处理器
 *
 * <p>用于菜单导入模板 / 菜单导出文件：菜单ID、父菜单ID 是 19 位雪花ID，
 * Excel 数值只有 15 位有效数字，一旦被当成数值存储就会末尾补零、父子引用失效。
 * 大数值在写入阶段由 {@code ExcelBigNumberConvert} 转成字符串，本处理器再负责：</p>
 * <ol>
 *     <li>已写入的单元格统一设为「文本」格式，数值型单元格同时转成字符串；</li>
 *     <li>把 sheet 各列的默认样式设为文本，用户后续在模板中输入/粘贴长数字时不会被 Excel 自动转数值。</li>
 * </ol>
 *
 * <p>只按需注册，不影响其它业务导出（用户、日志等仍保持原有数值/日期列）。</p>
 */
public class AllTextCellWriteHandler implements SheetWriteHandler, CellWriteHandler {

    private static final String TEXT_FORMAT = "@";

    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        if (writeSheetHolder.getExcelWriteHeadProperty() == null) {
            return;
        }
        Sheet sheet = writeSheetHolder.getSheet();
        Workbook workbook = writeWorkbookHolder.getWorkbook();
        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(TEXT_FORMAT));
        int columnCount = writeSheetHolder.getExcelWriteHeadProperty().getHeadMap().size();
        for (int i = 0; i < columnCount; i++) {
            sheet.setDefaultColumnStyle(i, textStyle);
        }
    }

    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        Cell cell = context.getCell();
        if (cell == null) {
            return;
        }
        // 数值单元格转成字符串（日期型保留，避免破坏日期展示）。
        // 注意：19 位 ID 在写入阶段已经被 ExcelBigNumberConvert 转为字符串，这里不会再被 double 精度影响。
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            String text = new BigDecimal(String.valueOf(cell.getNumericCellValue()))
                .stripTrailingZeros().toPlainString();
            cell.setCellValue(text);
        }
        // 必须改 WriteCellData 的样式：afterCellDispose 之后 FastExcel 还会按 WriteCellStyle 重设单元格样式，
        // 直接 cell.setCellStyle(...) 会被覆盖（与 DataWriteHandler 的做法一致）
        WriteCellData<?> cellData = context.getFirstCellData();
        if (cellData != null) {
            WriteCellStyle writeCellStyle = cellData.getOrCreateStyle();
            DataFormatData dataFormatData = new DataFormatData();
            dataFormatData.setIndex((short) 49);
            writeCellStyle.setDataFormatData(dataFormatData);
        }
    }
}
