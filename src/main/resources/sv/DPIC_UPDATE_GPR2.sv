`include "DPIC_TYPES_DEFINE.sv"
import "DPI-C" function void dpic_update_gpr(input `uint8_t id, input `uint32_t value);

module DPIC_UPDATE_GPR2 #(
    parameter GPR_NUM = 32,
    parameter DATA_WIDTH = 32
) (
    input   wire clk,
    input   wire rst,
    input   wire [$clog2(GPR_NUM)-1:0] id1,
    input   wire wen1,
    input   wire [DATA_WIDTH-1:0] wdata1,
    input   wire [$clog2(GPR_NUM)-1:0] id2,
    input   wire wen2,
    input   wire [DATA_WIDTH-1:0] wdata2
);

always @(posedge clk) begin
    if(!rst && id1!=0 && wen1) begin
        dpic_update_gpr({{8-$clog2(GPR_NUM){1'b0}}, id1}, wdata1);
    end
    if(!rst && id2!=0 && wen2) begin
        dpic_update_gpr({{8-$clog2(GPR_NUM){1'b0}}, id2}, wdata2);
    end
end
    
endmodule