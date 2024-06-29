`include "DPIC_TYPES_DEFINE.sv"
import "DPI-C" function void dpic_perf_bru(input `uint8_t fail);
module DPIC_PERF_BRU #(
    parameter DATA_WIDTH = 32
) (
    input   wire clk,
    input   wire rst,
    input   wire valid,
    input   wire [7:0] fail
);

always @(posedge clk) begin
    if(!rst && valid) begin
        dpic_perf_bru(fail);
    end
end
    
endmodule