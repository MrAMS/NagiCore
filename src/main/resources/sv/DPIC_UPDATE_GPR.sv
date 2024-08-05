`include "DPIC_TYPES_DEFINE.sv"
import "DPI-C" function void dpic_update_gpr(input `uint8_t id, input `uint32_t value);

module DPIC_UPDATE_GPR #(
    parameter GPR_NUM = 32,
    parameter DATA_WIDTH = 32
) (
    input   wire clk,
    input   wire rst,
    input   wire [$clog2(GPR_NUM)-1:0] id,
    input   wire wen,
    input   wire [DATA_WIDTH-1:0] wdata
);

always @(posedge clk) begin
    if(!rst && id!=0 && wen) begin
        dpic_update_gpr({{8-$clog2(GPR_NUM){1'b0}}, id}, wdata);
    end
end
    
endmodule