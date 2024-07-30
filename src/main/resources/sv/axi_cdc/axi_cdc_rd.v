/*

Copyright (c) 2019 Alex Forencich

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

*/

// Language: Verilog 2001

`resetall
`timescale 1ns / 1ps
`default_nettype none

/*
 * AXI4 lite clock domain crossing module (read)
 */
module axi_cdc_rd #
(
    // Width of data bus in bits
    parameter DATA_WIDTH = 32,
    // Width of address bus in bits
    parameter ADDR_WIDTH = 32,
    // Width of wstrb (width of data bus in words)
    parameter STRB_WIDTH = (DATA_WIDTH/8),
    // Width of ID
    parameter ID_WIDTH = 4
)
(
    /*
     * AXI lite slave interface
     */
    input  wire                   s_clk,
    input  wire                   s_rst,
    input  wire [ADDR_WIDTH-1:0]  s_axi_araddr,
    
    input  wire [ID_WIDTH-1:0]    s_axi_arid,
    input  wire [8-1:0]           s_axi_arlen,
    input  wire [3-1:0]           s_axi_arsize,
    input  wire [2-1:0]           s_axi_arburst,

    input  wire [2:0]             s_axi_arprot,
    input  wire                   s_axi_arvalid,
    output wire                   s_axi_arready,

    output wire [DATA_WIDTH-1:0]  s_axi_rdata,

    output wire [ID_WIDTH-1:0]    s_axi_rid,
    output wire                   s_axi_rlast,

    output wire [1:0]             s_axi_rresp,
    output wire                   s_axi_rvalid,
    input  wire                   s_axi_rready,

    /*
     * AXI lite master interface
     */
    input  wire                   m_clk,
    input  wire                   m_rst,
    output wire [ADDR_WIDTH-1:0]  m_axi_araddr,

    output wire [ID_WIDTH-1:0]    m_axi_arid,
    output wire [8-1:0]           m_axi_arlen,
    output wire [3-1:0]           m_axi_arsize,
    output wire [2-1:0]           m_axi_arburst,

    output wire [2:0]             m_axi_arprot,
    output wire                   m_axi_arvalid,
    input  wire                   m_axi_arready,
    input  wire [DATA_WIDTH-1:0]  m_axi_rdata,

    input  wire [ID_WIDTH-1:0]    m_axi_rid,
    input  wire                   m_axi_rlast,

    input  wire [1:0]             m_axi_rresp,
    input  wire                   m_axi_rvalid,
    output wire                   m_axi_rready
);

reg [1:0] s_state_reg = 2'd0;
reg s_flag_reg = 1'b0;
(* srl_style = "register" *)
reg s_flag_sync_reg_1 = 1'b0;
(* srl_style = "register" *)
reg s_flag_sync_reg_2 = 1'b0;

reg [1:0] m_state_reg = 2'd0;
reg m_flag_reg = 1'b0;
(* srl_style = "register" *)
reg m_flag_sync_reg_1 = 1'b0;
(* srl_style = "register" *)
reg m_flag_sync_reg_2 = 1'b0;

reg [ADDR_WIDTH-1:0]  s_axi_araddr_reg = {ADDR_WIDTH{1'b0}};

reg [ID_WIDTH-1:0] s_axi_arid_reg = {ID_WIDTH{1'b0}};
reg [8-1:0] s_axi_arlen_reg = {8{1'b0}};
reg [3-1:0] s_axi_arsize_reg = {3{1'b0}};
reg [2-1:0] s_axi_arburst_reg = {2{1'b0}};

reg [2:0]             s_axi_arprot_reg = 3'd0;
reg                   s_axi_arvalid_reg = 1'b0;
reg [DATA_WIDTH-1:0]  s_axi_rdata_reg = {DATA_WIDTH{1'b0}};

reg [ID_WIDTH-1:0]    s_axi_rid_reg = {ID_WIDTH{1'b0}};
reg                   s_axi_rlast_reg = 1'b0;

reg [1:0]             s_axi_rresp_reg = 2'b00;
reg                   s_axi_rvalid_reg = 1'b0;

reg [ADDR_WIDTH-1:0]  m_axi_araddr_reg = {ADDR_WIDTH{1'b0}};

reg [ID_WIDTH-1:0] m_axi_arid_reg = {ID_WIDTH{1'b0}};
reg [8-1:0] m_axi_arlen_reg = {8{1'b0}};
reg [3-1:0] m_axi_arsize_reg = {3{1'b0}};
reg [2-1:0] m_axi_arburst_reg = {2{1'b0}};

reg [2:0]             m_axi_arprot_reg = 3'd0;
reg                   m_axi_arvalid_reg = 1'b0;
reg [DATA_WIDTH-1:0]  m_axi_rdata_reg = {DATA_WIDTH{1'b0}};

reg [ID_WIDTH-1:0]    m_axi_rid_reg = {ID_WIDTH{1'b0}};
reg                   m_axi_rlast_reg = 1'b0;

reg [1:0]             m_axi_rresp_reg = 2'b00;
reg                   m_axi_rvalid_reg = 1'b1;

assign s_axi_arready = !s_axi_arvalid_reg && !s_axi_rvalid_reg;
assign s_axi_rdata = s_axi_rdata_reg;

assign s_axi_rid = s_axi_rid_reg;
assign s_axi_rlast = s_axi_rlast_reg;

assign s_axi_rresp = s_axi_rresp_reg;
assign s_axi_rvalid = s_axi_rvalid_reg;

assign m_axi_araddr = m_axi_araddr_reg;

assign m_axi_arid = m_axi_arid_reg;
assign m_axi_arlen = m_axi_arlen_reg;
assign m_axi_arsize = m_axi_arsize_reg;
assign m_axi_arburst = m_axi_arburst_reg;

assign m_axi_arprot = m_axi_arprot_reg;
assign m_axi_arvalid = m_axi_arvalid_reg;
assign m_axi_rready = !m_axi_rvalid_reg;

// slave side
always @(posedge s_clk) begin
    s_axi_rvalid_reg <= s_axi_rvalid_reg && !s_axi_rready;

    if (!s_axi_arvalid_reg && !s_axi_rvalid_reg) begin
        s_axi_araddr_reg <= s_axi_araddr;

        s_axi_arid_reg <= s_axi_arid;
        s_axi_arlen_reg <= s_axi_arlen;
        s_axi_arsize_reg <= s_axi_arsize;
        s_axi_arburst_reg <= s_axi_arburst;

        s_axi_arprot_reg <= s_axi_arprot;
        s_axi_arvalid_reg <= s_axi_arvalid;
    end

    case (s_state_reg)
        2'd0: begin
            if (s_axi_arvalid_reg) begin
                s_state_reg <= 2'd1;
                s_flag_reg <= 1'b1;
            end
        end
        2'd1: begin
            if (m_flag_sync_reg_2) begin
                s_state_reg <= 2'd2;
                s_flag_reg <= 1'b0;
                s_axi_rdata_reg <= m_axi_rdata_reg;

                s_axi_rid_reg <= m_axi_rid_reg;
                s_axi_rlast_reg <= m_axi_rlast_reg;

                s_axi_rresp_reg <= m_axi_rresp_reg;
                s_axi_rvalid_reg <= 1'b1;
            end
        end
        2'd2: begin
            if (!m_flag_sync_reg_2) begin
                s_state_reg <= 2'd0;
                s_axi_arvalid_reg <= 1'b0;
            end
        end
    endcase

    if (s_rst) begin
        s_state_reg <= 2'd0;
        s_flag_reg <= 1'b0;
        s_axi_arvalid_reg <= 1'b0;
        s_axi_rvalid_reg <= 1'b0;
    end
end

// synchronization
always @(posedge s_clk) begin
    m_flag_sync_reg_1 <= m_flag_reg;
    m_flag_sync_reg_2 <= m_flag_sync_reg_1;
end

always @(posedge m_clk) begin
    s_flag_sync_reg_1 <= s_flag_reg;
    s_flag_sync_reg_2 <= s_flag_sync_reg_1;
end

// master side
always @(posedge m_clk) begin
    m_axi_arvalid_reg <= m_axi_arvalid_reg && !m_axi_arready;

    if (!m_axi_rvalid_reg) begin
        m_axi_rdata_reg <= m_axi_rdata;

        m_axi_rid_reg <= m_axi_rid;
        m_axi_rlast_reg <= m_axi_rlast;

        m_axi_rresp_reg <= m_axi_rresp;
        m_axi_rvalid_reg <= m_axi_rvalid;
    end

    case (m_state_reg)
        2'd0: begin
            if (s_flag_sync_reg_2) begin
                m_state_reg <= 2'd1;
                m_axi_araddr_reg <= s_axi_araddr_reg;

                m_axi_arid_reg <= s_axi_arid_reg;
                m_axi_arlen_reg <= s_axi_arlen_reg;
                m_axi_arsize_reg <= s_axi_arsize_reg;
                m_axi_arburst_reg <= s_axi_arburst_reg;

                m_axi_arprot_reg <= s_axi_arprot_reg;
                m_axi_arvalid_reg <= 1'b1;
                m_axi_rvalid_reg <= 1'b0;
            end
        end
        2'd1: begin
            if (m_axi_rvalid_reg) begin
                m_flag_reg <= 1'b1;
                m_state_reg <= 2'd2;
            end
        end
        2'd2: begin
            if (!s_flag_sync_reg_2) begin
                m_state_reg <= 2'd0;
                m_flag_reg <= 1'b0;
            end
        end
    endcase

    if (m_rst) begin
        m_state_reg <= 2'd0;
        m_flag_reg <= 1'b0;
        m_axi_arvalid_reg <= 1'b0;
        m_axi_rvalid_reg <= 1'b1;
    end
end

endmodule

`resetall
