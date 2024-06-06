`include "DPIC_TYPES_DEFINE.sv"
import "DPI-C" function void dpic_bus_read(input `uint32_t addr, input `uint8_t size, output `uint32_t rdata);
import "DPI-C" function void dpic_bus_write(input `uint32_t addr, input `uint8_t wmask, input `uint32_t wdata);

module DPIC_SRAM #(
    parameter ADDR_WIDTH = 32,
    parameter DATA_WIDTH = 32
) (
    input   wire clk,
    input   wire rst,
    input   wire data_req,
    input   wire [ADDR_WIDTH-1:0] data_addr,
    input   wire [$clog2(DATA_WIDTH)-1:0] data_wmask,
    input   wire [1:0] data_size,
    input   wire [DATA_WIDTH-1:0] data_wdata,
    output  reg [DATA_WIDTH-1:0] data_rdata,
    output  wire data_stall,
    input   wire data_uncache
    // output  wire data_valid
);
wire [DATA_WIDTH-1:0] rdata_wire;

// assign rdata = rdata_reg;

// assign data_valid = 1;
assign data_stall = 0;


always @(posedge clk) begin
    if (rst) begin
        data_rdata <= 0;
    end else begin
        if (data_req) begin
            if(|data_wmask) begin
                dpic_bus_write(data_addr, {{8-$clog2(DATA_WIDTH){1'b0}}, data_wmask}, data_wdata);
            end else begin
                dpic_bus_read(data_addr, {6'b0, data_size}, rdata_wire);
                data_rdata <= rdata_wire;
            end
        end
    end
end

endmodule